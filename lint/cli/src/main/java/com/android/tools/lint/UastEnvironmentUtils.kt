/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint

import com.intellij.codeInsight.CustomExceptionHandler
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.CompactVirtualFileSet
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSet
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.pom.java.LanguageFeatureProvider
import it.unimi.dsi.fastutil.ints.IntSet
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.pathString
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.lifetime.KtDefaultLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.standalone.KtAlwaysAccessibleLifetimeTokenProvider
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.cli.common.messages.GradleStyleMessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.evaluation.UEvaluatorExtension
import org.jetbrains.uast.java.JavaUastLanguagePlugin
import org.jetbrains.uast.kotlin.evaluation.KotlinEvaluatorExtension

internal fun createCommonKotlinCompilerConfig(): CompilerConfiguration {
  val config = CompilerConfiguration()

  config.put(CommonConfigurationKeys.MODULE_NAME, "lint-module")

  // By default, the Kotlin compiler will dispose the application environment when there
  // are no projects left. However, that behavior is poorly tested and occasionally buggy
  // (see KT-45289). So, instead we manage the application lifecycle manually.
  CompilerSystemProperties.KOTLIN_COMPILER_ENVIRONMENT_KEEPALIVE_PROPERTY.value = "true"

  // We're not running compiler checks, but we still want to register a logger
  // in order to see warnings related to misconfiguration.
  val logger = PrintingMessageCollector(System.err, GradleStyleMessageRenderer(), false)
  config.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, logger)

  // The Kotlin compiler uses a fast, ASM-based class file reader.
  // However, Lint still relies on representing class files with PSI.
  config.put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

  // We don't bundle .dll files in the Gradle plugin for native file system access;
  // prevent warning logs on Windows when it's not found (see b.android.com/260180).
  System.setProperty("idea.use.native.fs.for.win", "false")

  config.put(JVMConfigurationKeys.NO_JDK, true)

  // To work around any `bin/idea.properties` issues, e.g.,
  // https://youtrack.jetbrains.com/issue/KT-56279 (IJ 223)
  // https://youtrack.jetbrains.com/issue/KT-62039 (IJ 232)
  val bin = Files.createTempDirectory("fake_bin")
  bin.toFile().deleteOnExit()
  System.setProperty(PathManager.PROPERTY_HOME_PATH, bin.pathString)

  return config
}

/** Returns a new [LanguageVersionSettings] with KMP enabled. */
fun LanguageVersionSettings.withKMPEnabled(): LanguageVersionSettings {
  return LanguageVersionSettingsImpl(
    this.languageVersion,
    this.apiVersion,
    emptyMap(),
    mapOf(LanguageFeature.MultiPlatformProjects to LanguageFeature.State.ENABLED),
  )
}

internal fun configureProjectEnvironment(
  project: MockProject,
  config: UastEnvironment.Configuration,
) {
  // Annotation support.
  project.registerService(
    ExternalAnnotationsManager::class.java,
    LintExternalAnnotationsManager::class.java,
  )
  project.registerService(
    InferredAnnotationsManager::class.java,
    LintInferredAnnotationsManager::class.java,
  )

  // Java language level.
  val javaLanguageLevel = config.javaLanguageLevel
  if (javaLanguageLevel != null) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = javaLanguageLevel
  }

  // TODO(b/283351708): Migrate to using UastFacade/UastLanguagePlugin instead,
  //  even including lint checks shipped in a binary form?!
  @Suppress("DEPRECATION") project.registerService(UastContext::class.java, UastContext(project))
}

@OptIn(KtAnalysisApiInternals::class)
internal fun MockProject.registerKtLifetimeTokenProvider() {
  // TODO: remove this after a couple release cycles
  //  to make sure Lint clients, including androidx runtime, catch up.
  @Suppress("DEPRECATION") registerService(KtDefaultLifetimeTokenProvider::class.java)
  registerService(
    KtLifetimeTokenProvider::class.java,
    KtAlwaysAccessibleLifetimeTokenProvider::class.java,
  )
}

// In parallel builds the Kotlin compiler will reuse the application environment
// (see KotlinCoreEnvironment.getOrCreateApplicationEnvironmentForProduction).
// So we need a lock to ensure that we only configure the application environment once.
internal val appLock = ReentrantLock()
private var appConfigured = false

internal fun configureApplicationEnvironment(
  appEnv: CoreApplicationEnvironment,
  configurator: (CoreApplicationEnvironment) -> Unit,
) {
  check(appLock.isHeldByCurrentThread)

  if (appConfigured) return

  if (!Logger.isInitialized()) {
    Logger.setFactory(::IdeaLoggerForLint)
  }

  // Mark the registry as loaded, otherwise there are warnings upon registry value lookup.
  Registry.markAsLoaded()

  // The Kotlin compiler does not use UAST, so we must configure it ourselves.
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    UastLanguagePlugin.extensionPointName,
    UastLanguagePlugin::class.java,
  )
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    UEvaluatorExtension.EXTENSION_POINT_NAME,
    UEvaluatorExtension::class.java,
  )
  appEnv.addExtension(UastLanguagePlugin.extensionPointName, JavaUastLanguagePlugin())

  appEnv.addExtension(UEvaluatorExtension.EXTENSION_POINT_NAME, KotlinEvaluatorExtension())

  configurator(appEnv)

  // These extensions points seem to be needed too, probably because Lint
  // triggers different IntelliJ code paths than the Kotlin compiler does.
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    CustomExceptionHandler.KEY,
    CustomExceptionHandler::class.java,
  )
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    DiagnosticSuppressor.EP_NAME,
    DiagnosticSuppressor::class.java,
  )
  CoreApplicationEnvironment.registerApplicationExtensionPoint(
    LanguageFeatureProvider.EXTENSION_POINT_NAME,
    LanguageFeatureProvider::class.java,
  )

  appConfigured = true
  Disposer.register(appEnv.parentDisposable, Disposable { appConfigured = false })
}

internal fun reRegisterProgressManager(application: MockApplication) {
  // The ProgressManager service is registered early in CoreApplicationEnvironment, we need to
  // remove it first.
  application.picoContainer.unregisterComponent(ProgressManager::class.java.name)
  application.registerService(
    ProgressManager::class.java,
    object : CoreProgressManager() {
      override fun doCheckCanceled() {
        // Do nothing
      }

      override fun isInNonCancelableSection() = true
    },
  )
}

// KT-56277: [CompactVirtualFileSetFactory] is package-private, so we introduce our own default-ish
// implementation.
internal object LintVirtualFileSetFactory : VirtualFileSetFactory {
  override fun createCompactVirtualFileSet(): VirtualFileSet {
    return CompactVirtualFileSet(IntSet.of())
  }

  override fun createCompactVirtualFileSet(
    files: MutableCollection<out VirtualFile>
  ): VirtualFileSet {
    return CompactVirtualFileSet(IntSet.of()).apply { addAll(files) }
  }
}

// Most Logger.error() calls exist to trigger bug reports but are
// otherwise recoverable. E.g. see commit 3260e41111 in the Kotlin compiler.
// Thus we want to log errors to stderr but not throw exceptions (similar to the IDE).
private class IdeaLoggerForLint(category: String) : DefaultLogger(category) {
  override fun error(message: String?, t: Throwable?, vararg details: String?) {
    if (IdeaLoggerForLint::class.java.desiredAssertionStatus()) {
      throw AssertionError(message, t)
    } else {
      if (shouldDumpExceptionToStderr()) {
        System.err.println("ERROR: " + message + detailsToString(*details) + attachmentsToString(t))
        t?.printStackTrace(System.err)
      }
    }
  }
}
