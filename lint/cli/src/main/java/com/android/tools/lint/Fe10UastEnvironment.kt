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

import com.android.tools.lint.UastEnvironment.Companion.getKlibPaths
import com.android.tools.lint.UastEnvironment.Companion.kotlinLibrary
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileSetFactory
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import com.intellij.pom.java.InternalPersistentJavaLanguageLevelReaderService
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.impl.PsiNameHelperImpl
import java.io.File
import kotlin.concurrent.withLock
import org.jetbrains.kotlin.analysis.api.descriptors.CliFe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.KtFe10AnalysisHandlerExtension
import org.jetbrains.kotlin.analysis.api.descriptors.KtFe10AnalysisSessionProvider
import org.jetbrains.kotlin.analysis.api.descriptors.references.ReadWriteAccessCheckerDescriptorsImpl
import org.jetbrains.kotlin.analysis.api.impl.base.references.HLApiReferenceProviderService
import org.jetbrains.kotlin.analysis.api.session.KtAnalysisSessionProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInsVirtualFileProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInsVirtualFileProviderCliImpl
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.decompiler.stub.file.DummyFileAttributeService
import org.jetbrains.kotlin.analysis.decompiler.stub.file.FileAttributeService
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.project.structure.impl.buildKtModuleProviderByCompilerConfiguration
import org.jetbrains.kotlin.analysis.project.structure.impl.getPsiFilesFromPaths
import org.jetbrains.kotlin.analysis.project.structure.impl.getSourceFilePaths
import org.jetbrains.kotlin.analysis.providers.KotlinAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.providers.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.providers.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.providers.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.providers.impl.KotlinFakeClsStubsCache
import org.jetbrains.kotlin.analysis.providers.impl.KotlinStaticAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.providers.impl.KotlinStaticDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.providers.impl.KotlinStaticModificationTrackerFactory
import org.jetbrains.kotlin.analysis.providers.impl.KotlinStaticPackageProviderFactory
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.CliModuleAnnotationsResolver
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.references.KotlinReferenceProviderContributor
import org.jetbrains.kotlin.idea.references.ReadWriteAccessChecker
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.psi.KotlinReferenceProvidersService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.references.fe10.base.DummyKtFe10ReferenceResolutionHelper
import org.jetbrains.kotlin.references.fe10.base.KtFe10KotlinReferenceProviderContributor
import org.jetbrains.kotlin.references.fe10.base.KtFe10ReferenceResolutionHelper
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ModuleAnnotationsResolver
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.CliKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.internal.UastAnalysisHandlerExtension

/**
 * This class is FE1.0 version of [UastEnvironment].
 *
 * After FIR (Frontend IR) is developed, the old frontend is retroactively named as FE1.0. So is
 * Kotlin UAST based on it.
 */
class Fe10UastEnvironment
private constructor(
  // Luckily, the Kotlin compiler already has the machinery for creating an IntelliJ
  // application environment (because Kotlin uses IntelliJ to parse Java). So most of
  // the work here is delegated to the Kotlin compiler.
  private val kotlinCompilerEnv: KotlinCoreEnvironment,
  override val projectDisposable: Disposable,
) : UastEnvironment {
  override val coreAppEnv: CoreApplicationEnvironment
    get() = kotlinCompilerEnv.projectEnvironment.environment

  override val ideaProject: MockProject
    get() = kotlinCompilerEnv.projectEnvironment.project

  override val kotlinCompilerConfig: CompilerConfiguration
    get() = kotlinCompilerEnv.configuration

  private val klibs = mutableListOf<KotlinLibrary>()

  class Configuration
  private constructor(override val kotlinCompilerConfig: CompilerConfiguration) :
    UastEnvironment.Configuration {
    override var javaLanguageLevel: LanguageLevel? = null

    // klibs indexed by paths to avoid duplicates
    internal val klibs = hashMapOf<String, KotlinLibrary>()

    // Legacy merging behavior for Fe 1.0
    override fun addModules(
      modules: List<UastEnvironment.Module>,
      bootClassPaths: Iterable<File>?,
    ) {
      kotlinLanguageLevel =
        modules.map(UastEnvironment.Module::kotlinLanguageLevel).reduce { r, t ->
          // TODO: How to accumulate `analysisFlags` and `specificFeatures` ?
          LanguageVersionSettingsImpl(
            r.languageVersion.coerceAtLeast(t.languageVersion),
            r.apiVersion.coerceAtLeast(t.apiVersion),
          )
        }
      UastEnvironment.Configuration.mergeRoots(modules, bootClassPaths).let { (sources, classPaths)
        ->
        val allKlibPaths =
          modules.flatMap { it.klibs.keys.map(File::getAbsolutePath) } +
            kotlinCompilerConfig.getKlibPaths()
        for (p in allKlibPaths) {
          klibs.computeIfAbsent(p, ::kotlinLibrary)
        }
        addSourceRoots(sources.toList())
        addClasspathRoots(classPaths.toList())
      }
    }

    companion object {
      @JvmStatic
      fun create(enableKotlinScripting: Boolean): Configuration =
        Configuration(createKotlinCompilerConfig(enableKotlinScripting))
    }
  }

  /**
   * Analyzes the given files so that PSI/UAST resolve works correctly.
   *
   * For now, only Kotlin files need to be analyzed upfront; Java code is resolved lazily. However,
   * this method must still be called for Java-only projects in order to properly initialize the PSI
   * machinery.
   *
   * Calling this function multiple times clears previous analysis results.
   */
  override fun analyzeFiles(ktFiles: List<File>) {
    val ktPsiFiles = mutableListOf<KtFile>()

    // Convert files to KtFiles.
    val fs = StandardFileSystems.local()
    val psiManager = PsiManager.getInstance(ideaProject)
    for (ktFile in ktFiles) {
      val vFile = fs.findFileByPath(ktFile.absolutePath) ?: continue
      val ktPsiFile = psiManager.findFile(vFile) as? KtFile ?: continue
      ktPsiFiles.add(ktPsiFile)
    }

    // TODO: This is a hack needed because TopDownAnalyzerFacadeForJVM calls
    //  KotlinCoreEnvironment.createPackagePartProvider(), which permanently adds additional
    //  PackagePartProviders to the environment. This significantly slows down resolve over
    //  time. The root issue is that KotlinCoreEnvironment was not designed to be reused
    //  repeatedly for multiple analyses---which we do when checkDependencies=true. This hack
    //  should be removed when we move to a model where UastEnvironment is used only once.
    resetPackagePartProviders()

    val perfManager = kotlinCompilerConfig.get(CLIConfigurationKeys.PERF_MANAGER)
    perfManager?.notifyAnalysisStarted()

    // Run the Kotlin compiler front end.
    // The result is implicitly associated with the IntelliJ project environment.
    // TODO: Consider specifying a sourceModuleSearchScope, which can be used to support
    //  partial compilation by giving the Kotlin compiler access to the compiled output
    //  of the module being analyzed. See KotlinToJVMBytecodeCompiler for an example.
    TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
      ideaProject,
      ktPsiFiles,
      CliBindingTraceForLint(ideaProject),
      kotlinCompilerConfig,
      kotlinCompilerEnv::createPackagePartProvider,
      klibList = klibs,
    )

    perfManager?.notifyAnalysisFinished()
  }

  private fun resetPackagePartProviders() {
    run {
      // Clear KotlinCoreEnvironment.packagePartProviders.
      val field = KotlinCoreEnvironment::class.java.getDeclaredField("packagePartProviders")
      field.isAccessible = true
      val list = field.get(kotlinCompilerEnv) as MutableList<*>
      list.clear()
    }
    run {
      // Clear CliModuleAnnotationsResolver.packagePartProviders.
      val field = CliModuleAnnotationsResolver::class.java.getDeclaredField("packagePartProviders")
      field.isAccessible = true
      val instance = ModuleAnnotationsResolver.getInstance(ideaProject)
      val list = field.get(instance) as MutableList<*>
      list.clear()
    }
  }

  companion object {
    @JvmStatic
    fun create(config: Configuration): Fe10UastEnvironment {
      val parentDisposable = Disposer.newDisposable("Fe10UastEnvironment.create")
      val kotlinEnv = createKotlinCompilerEnv(parentDisposable, config)
      return Fe10UastEnvironment(kotlinEnv, parentDisposable).apply {
        klibs.addAll(config.klibs.values)
      }
    }
  }
}

@OptIn(ExperimentalCompilerApi::class)
private fun createKotlinCompilerConfig(enableKotlinScripting: Boolean): CompilerConfiguration {
  val config = createCommonKotlinCompilerConfig()

  // Registers the scripting compiler plugin to support build.gradle.kts files.
  if (enableKotlinScripting) {
    config.add(
      ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS,
      ScriptingCompilerConfigurationComponentRegistrar(),
    )
  }

  return config
}

private fun createKotlinCompilerEnv(
  parentDisposable: Disposable,
  config: Fe10UastEnvironment.Configuration,
): KotlinCoreEnvironment {
  val env =
    KotlinCoreEnvironment.createForProduction(
      parentDisposable,
      config.kotlinCompilerConfig,
      JVM_CONFIG_FILES,
    )
  appLock.withLock { configureFe10ApplicationEnvironment(env.projectEnvironment.environment) }
  configureFe10ProjectEnvironment(env.projectEnvironment, config)

  return env
}

private fun configureFe10ProjectEnvironment(
  env: KotlinCoreProjectEnvironment,
  config: Fe10UastEnvironment.Configuration,
) {
  val project = env.project
  // UAST support.
  AnalysisHandlerExtension.registerExtension(project, UastAnalysisHandlerExtension())
  project.registerService(
    KotlinUastResolveProviderService::class.java,
    CliKotlinUastResolveProviderService::class.java,
  )

  // PsiNameHelper is used by Kotlin UAST.
  project.registerService(PsiNameHelper::class.java, PsiNameHelperImpl::class.java)

  configureProjectEnvironment(project, config)

  configureAnalysisApiServices(env, config)
}

private fun configureAnalysisApiServices(
  env: KotlinCoreProjectEnvironment,
  config: Fe10UastEnvironment.Configuration,
) {
  val project = env.project
  // Analysis API Base, i.e., base services for FE1.0 and FIR
  // But, for FIR, AA session builder already register these
  project.registerService(
    KotlinModificationTrackerFactory::class.java,
    KotlinStaticModificationTrackerFactory::class.java,
  )
  project.registerKtLifetimeTokenProvider()

  val ktFiles = getPsiFilesFromPaths<KtFile>(env, getSourceFilePaths(config.kotlinCompilerConfig))

  project.registerService(
    ProjectStructureProvider::class.java,
    buildKtModuleProviderByCompilerConfiguration(env, config.kotlinCompilerConfig, ktFiles),
  )

  project.registerService(
    KotlinAnnotationsResolverFactory::class.java,
    KotlinStaticAnnotationsResolverFactory(project, ktFiles),
  )
  project.registerService(
    KotlinDeclarationProviderFactory::class.java,
    KotlinStaticDeclarationProviderFactory(project, ktFiles),
  )
  project.registerService(
    KotlinPackageProviderFactory::class.java,
    KotlinStaticPackageProviderFactory(project, ktFiles),
  )

  project.registerService(
    KotlinReferenceProvidersService::class.java,
    HLApiReferenceProviderService::class.java,
  )

  // Analysis API FE1.0-specific
  project.registerService(
    KtAnalysisSessionProvider::class.java,
    KtFe10AnalysisSessionProvider(project),
  )
  project.registerService(Fe10AnalysisFacade::class.java, CliFe10AnalysisFacade::class.java)
  // Duplicate: already registered at [KotlinCoreEnvironment]
  // project.registerService(ModuleVisibilityManager::class.java,
  // CliModuleVisibilityManagerImpl(enabled = true))
  project.registerService(
    ReadWriteAccessChecker::class.java,
    ReadWriteAccessCheckerDescriptorsImpl(),
  )
  project.registerService(
    KotlinReferenceProviderContributor::class.java,
    KtFe10KotlinReferenceProviderContributor::class.java,
  )

  AnalysisHandlerExtension.registerExtension(project, KtFe10AnalysisHandlerExtension())
}

private fun configureFe10ApplicationEnvironment(appEnv: CoreApplicationEnvironment) {
  configureApplicationEnvironment(appEnv) {
    it.addExtension(UastLanguagePlugin.extensionPointName, KotlinUastLanguagePlugin())

    it.application.registerService(
      BaseKotlinUastResolveProviderService::class.java,
      CliKotlinUastResolveProviderService::class.java,
    )

    it.application.registerService(
      KtFe10ReferenceResolutionHelper::class.java,
      DummyKtFe10ReferenceResolutionHelper,
    )

    KotlinCoreEnvironment.underApplicationLock {
      if (it.application.getServiceIfCreated(KotlinFakeClsStubsCache::class.java) == null) {
        it.application.registerService(KotlinFakeClsStubsCache::class.java)
        it.application.registerService(
          BuiltInsVirtualFileProvider::class.java,
          BuiltInsVirtualFileProviderCliImpl(appEnv.jarFileSystem as CoreJarFileSystem),
        )
        it.application.registerService(ClsKotlinBinaryClassCache::class.java)
        it.application.registerService(
          FileAttributeService::class.java,
          DummyFileAttributeService::class.java,
        )
      }
    }

    if (it.application.getServiceIfCreated(VirtualFileSetFactory::class.java) == null) {
      // Note that this app-level service should be initialized before any other entities
      // attempt to instantiate [FilesScope]
      // For FE1.0 UAST, the first attempt will be made during project env setup,
      // so any place inside this app env setup is safe.
      it.application.registerService(VirtualFileSetFactory::class.java, LintVirtualFileSetFactory)
    }
    if (
      it.application.getServiceIfCreated(
        InternalPersistentJavaLanguageLevelReaderService::class.java
      ) == null
    ) {
      it.application.registerService(
        InternalPersistentJavaLanguageLevelReaderService::class.java,
        InternalPersistentJavaLanguageLevelReaderService.DefaultImpl(),
      )
    }
    reRegisterProgressManager(it.application)
  }
}

// A Kotlin compiler BindingTrace optimized for Lint.
private class CliBindingTraceForLint(project: Project) : CliBindingTrace(project) {
  override fun <K, V> record(slice: WritableSlice<K, V>, key: K, value: V) {
    // Copied from NoScopeRecordCliBindingTrace.
    when (slice) {
      BindingContext.LEXICAL_SCOPE,
      BindingContext.DATA_FLOW_INFO_BEFORE -> return
    }
    super.record(slice, key, value)
  }

  // Lint does not need compiler checks, so disable them to improve performance slightly.
  override fun wantsDiagnostics(): Boolean = false

  override fun report(diagnostic: Diagnostic) {
    // Even with wantsDiagnostics=false, some diagnostics still come through. Ignore them.
    // Note: this is a great place to debug errors such as unresolved references.
  }
}
