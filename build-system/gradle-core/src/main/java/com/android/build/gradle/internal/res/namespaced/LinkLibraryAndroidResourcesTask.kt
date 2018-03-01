/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidBuilderTask
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.sdklib.IAndroidTarget
import com.android.utils.FileUtils
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.function.Supplier
import javax.inject.Inject

/**
 * Task to link the resources in a library project into an AAPT2 static library.
 */
@CacheableTask
open class LinkLibraryAndroidResourcesTask @Inject constructor(private val workerExecutor: WorkerExecutor) :
        AndroidBuilderTask() {

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var manifestFile: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) lateinit var inputResourcesDirectories: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var libraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) lateinit var sharedLibraryDependencies: FileCollection private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional var featureDependencies: FileCollection? = null; private set
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) @get:Optional var tested: FileCollection? = null; private set

    @get:Internal lateinit var packageForRSupplier: Supplier<String> private set
    @Input fun getPackageForR() = packageForRSupplier.get()

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var aapt2FromMaven: FileCollection private set

    @get:OutputDirectory lateinit var aaptIntermediateDir: File private set
    @get:OutputFile lateinit var rDotTxt: File private set
    @get:OutputFile lateinit var staticLibApk: File private set

    @TaskAction
    fun taskAction() {

        val imports = ImmutableList.builder<File>()
        // Link against library dependencies
        imports.addAll(libraryDependencies.files)
        imports.addAll(sharedLibraryDependencies.files)

        // Link against features
        featureDependencies?.let {
            imports.addAll(
                    it.files
                            .map { ExistingBuildElements.from(InternalArtifactType.PROCESSED_RES, it) }
                            .filterNot { it.isEmpty() }
                            .map { splitOutputs -> splitOutputs.single().outputFile })
        }

        val request = AaptPackageConfig(
                androidJarPath = builder.target.getPath(IAndroidTarget.ANDROID_JAR),
                manifestFile = manifestFile.singleFile,
                options = AaptOptions(null, false, null),
                resourceDirs = ImmutableList.copyOf(inputResourcesDirectories.asIterable()),
                staticLibrary = true,
                imports = imports.build(),
                resourceOutputApk = staticLibApk,
                variantType = VariantTypeImpl.LIBRARY,
                customPackageForR = getPackageForR(),
                symbolOutputDir = rDotTxt.parentFile,
                intermediateDir = aaptIntermediateDir)

        val aapt2ServiceKey = registerAaptService(
            aapt2FromMaven = aapt2FromMaven,
            logger = iLogger
        )
        workerExecutor.submit(Aapt2LinkRunnable::class.java) {
            it.isolationMode = IsolationMode.NONE
            it.setParams(Aapt2LinkRunnable.Params(aapt2ServiceKey, request))
        }
    }

    class ConfigAction(
            private val scope: VariantScope,
            private val staticLibApk: File,
            private val rDotTxt: File) : TaskConfigAction<LinkLibraryAndroidResourcesTask> {

        override fun getName() = scope.getTaskName("link", "Resources")

        override fun getType() = LinkLibraryAndroidResourcesTask::class.java

        override fun execute(task: LinkLibraryAndroidResourcesTask) {
            task.variantName = scope.fullVariantName
            task.manifestFile = scope.getOutput(InternalArtifactType.STATIC_LIBRARY_MANIFEST)
            task.inputResourcesDirectories = scope.getOutput(InternalArtifactType.RES_COMPILED_FLAT_FILES)
            task.libraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_STATIC_LIBRARY)
            task.sharedLibraryDependencies =
                    scope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.RES_SHARED_STATIC_LIBRARY)

            if (scope.variantData.type.isApk && !scope.variantData.type.isBaseModule) {
                task.featureDependencies =
                        scope.getArtifactFileCollection(
                                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH,
                                AndroidArtifacts.ArtifactScope.MODULE,
                                AndroidArtifacts.ArtifactType.FEATURE_RESOURCE_PKG)
            }

            val testedScope = scope.testedVariantData?.scope
            if (testedScope != null) {
                task.tested = testedScope.getOutput(InternalArtifactType.RES_STATIC_LIBRARY)
            }

            task.aaptIntermediateDir =
                    FileUtils.join(
                            scope.globalScope.intermediatesDir, "res-link-intermediate", scope.variantConfiguration.dirName)
            task.staticLibApk = staticLibApk
            task.setAndroidBuilder(scope.globalScope.androidBuilder)
            task.packageForRSupplier = Suppliers.memoize(scope.variantConfiguration::getOriginalApplicationId)
            task.rDotTxt = rDotTxt
            task.aapt2FromMaven = getAapt2FromMaven(scope.globalScope)
        }
    }

}
