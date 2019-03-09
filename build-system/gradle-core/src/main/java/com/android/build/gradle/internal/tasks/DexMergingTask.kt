/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.api.transform.TransformException
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.api.artifact.singleFile
import com.android.build.gradle.internal.crash.PluginCrashReporter
import com.android.build.gradle.internal.dependency.getAttributeMap
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.transforms.DexMergerTransformCallable
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.builder.dexing.DexMergerTool
import com.android.builder.dexing.DexingType
import com.android.ide.common.blame.Message
import com.android.ide.common.blame.ParsingProcessOutputHandler
import com.android.ide.common.blame.parser.DexParser
import com.android.ide.common.blame.parser.ToolOutputParser
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessOutput
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.android.utils.PathUtils.toSystemIndependentPath
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Throwables
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.Serializable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Dex merging task. This task will merge all specified dex files, using the specified parameters.
 *
 * If handles all dexing types, as specified in [DexingType].
 *
 * One of the interesting properties is [mergingThreshold]. For dex file inputs, this property will
 * determine if dex files can be just copied to output, or we have to merge them. If the number of
 * dex files is at least [mergingThreshold], the files will be merged in a single invocation.
 * Otherwise, we will just copy the dex files to the output directory.
 *
 * This task is not incremental. Any input change will trigger full processing. However, due to
 * nature of dex merging, making it incremental will not bring any benefits: 1) if a project dex
 * file changes, we will need to at least re-merge entire project; 2) if an external library
 * changes, at least all external libraries will be re-merged; 3) if a library project dex file
 * changes we will at least need to either re-merge all library dex files, or copy them to output.
 *
 * As you can see, only scenario in which we are doing some more work is when a library project dex
 * file changes, and when we copy those files to output. An optimization would be to copy only the
 * changed dex file. However, just copying files is reasonable fast, so this should not result in an
 * performance regression.
 */
@CacheableTask
open class DexMergingTask @Inject constructor(workerExecutor: WorkerExecutor) :
    AndroidVariantTask() {

    private val workers: WorkerExecutorFacade = Workers.preferWorkers(project.name, path, workerExecutor)

    @get:Input
    lateinit var dexingType: DexingType
        private set

    @get:Input
    lateinit var dexMerger: DexMergerTool
        private set

    @get:Input
    var minSdkVersion: Int = 0
        private set

    @get:Input
    var isDebuggable: Boolean = true
        private set

    @get:Input
    var mergingThreshold: Int = 0
        private set

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    var mainDexListFile: BuildableArtifact? = null
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var dexFiles: FileCollection
        private set

    // Dummy folder, used as a way to set up dependency
    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    var duplicateClassesCheck: BuildableArtifact? = null
        private set

    @get:OutputDirectory
    lateinit var outputDir: File
        private set

    @get:Internal
    lateinit var errorFormatMode: SyncOptions.ErrorFormatMode
        private set

    @TaskAction
    fun taskAction() {
        workers.use {
            it.submit(
                DexMergingTaskRunnable::class.java, DexMergingParams(
                    dexingType,
                    errorFormatMode,
                    dexMerger,
                    minSdkVersion,
                    isDebuggable,
                    mergingThreshold,
                    mainDexListFile?.singleFile(),
                    dexFiles.files,
                    outputDir
                )
            )
        }
    }

    class CreationAction @JvmOverloads constructor(
        variantScope: VariantScope,
        private val action: DexMergingAction,
        private val dexingType: DexingType,
        private val outputType: InternalArtifactType = InternalArtifactType.DEX
    ) : VariantTaskCreationAction<DexMergingTask>(variantScope) {

        private val internalName: String = when (action) {
            DexMergingAction.MERGE_LIBRARY_PROJECTS -> variantScope.getTaskName("mergeLibDex")
            DexMergingAction.MERGE_EXTERNAL_LIBS -> variantScope.getTaskName("mergeExtDex")
            DexMergingAction.MERGE_PROJECT -> variantScope.getTaskName("mergeProjectDex")
            DexMergingAction.MERGE_ALL -> variantScope.getTaskName("mergeDex")
        }

        override val name = internalName
        override val type = DexMergingTask::class.java

        private lateinit var output: File

        override fun preConfigure(taskName: String) {
            output = variantScope.artifacts.appendArtifact(outputType, taskName)
        }

        override fun configure(task: DexMergingTask) {
            super.configure(task)

            task.dexFiles = getDexFiles(action)
            task.mergingThreshold = getMergingThreshold(action, task)

            task.dexingType = dexingType
            if (DexMergingAction.MERGE_ALL == action && dexingType === DexingType.LEGACY_MULTIDEX) {
                task.mainDexListFile =
                        variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
            }

            task.errorFormatMode =
                SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
            task.dexMerger = variantScope.dexMerger
            task.minSdkVersion = variantScope.minSdkVersion.featureLevel
            task.isDebuggable = variantScope.variantConfiguration.buildType.isDebuggable
            if (variantScope.globalScope.projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
                task.duplicateClassesCheck = variantScope.artifacts.getFinalArtifactFiles(
                    InternalArtifactType.DUPLICATE_CLASSES_CHECK
                )
            }
            task.outputDir = output
        }

        private fun getDexFiles(action: DexMergingAction): FileCollection {
            val minSdk = variantScope.minSdkVersion.featureLevel
            val isDebuggable = variantScope.variantConfiguration.buildType.isDebuggable
            val attributes = getAttributeMap(minSdk, isDebuggable)

            fun forAction(action: DexMergingAction): FileCollection {
                when (action) {
                    DexMergingAction.MERGE_EXTERNAL_LIBS -> {
                        return variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.EXTERNAL,
                            AndroidArtifacts.ArtifactType.DEX,
                            attributes
                        )
                    }
                    DexMergingAction.MERGE_LIBRARY_PROJECTS -> {
                        return variantScope.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.MODULE,
                            AndroidArtifacts.ArtifactType.DEX,
                            attributes
                        )
                    }
                    DexMergingAction.MERGE_PROJECT -> {
                        val streams =
                            variantScope.transformManager.getStreams(StreamFilter.DEX_ARCHIVE)
                        val files =
                            variantScope.globalScope.project.files(*streams.stream().map { it.fileCollection }.toArray())
                        val variantType = variantScope.type
                        if (variantType.isTestComponent && variantType.isApk) {
                            val testedVariantData =
                                checkNotNull(variantScope.testedVariantData) { "Test component without testedVariantData" }
                            if (testedVariantData.type.isAar) {
                                files.from(
                                    testedVariantData.scope.artifacts.getFinalArtifactFiles(
                                        InternalArtifactType.DEX
                                    )
                                )
                            }
                        }

                        return files
                    }
                    DexMergingAction.MERGE_ALL -> {
                        val external = if (dexingType == DexingType.LEGACY_MULTIDEX) {
                            // we have to dex it
                            forAction(DexMergingAction.MERGE_EXTERNAL_LIBS)
                        } else {
                            // we merge external dex in a separate task
                            variantScope.artifacts
                                .getFinalArtifactFiles(InternalArtifactType.EXTERNAL_LIBS_DEX)
                                .get()
                        }
                        return forAction(DexMergingAction.MERGE_PROJECT) +
                                forAction(DexMergingAction.MERGE_LIBRARY_PROJECTS) +
                                external
                    }
                }
            }

            return forAction(action)
        }

        /**
         * Get the number of dex files that will trigger merging of those files in a single
         * invocation. Project and external libraries dex files are always merged as much as possible,
         * so this only matters for the library projects dex files. See [LIBRARIES_MERGING_THRESHOLD]
         * for details.
         */
        private fun getMergingThreshold(action: DexMergingAction, task: DexMergingTask): Int {
            return when (action) {
                DexMergingAction.MERGE_LIBRARY_PROJECTS ->
                    when {
                        variantScope.minSdkVersion.featureLevel < 23 -> {
                            task.outputs.cacheIf { getAllRegularFiles(task.dexFiles.files).size < LIBRARIES_MERGING_THRESHOLD }
                            LIBRARIES_MERGING_THRESHOLD
                        }
                        else -> Integer.MAX_VALUE
                    }
                else -> 0
            }
        }
    }
}

/**
 * This returns a list of files from a set of files. If a file is in the set of files it is
 * added to the resulting set. If it is a directory, all files all collected recursively, and they
 * are sorted. This ensures that files from a single directory are always in deterministic order.
 *
 * We do not sort all files from a file collection as Gradle ensures consistent ordering of file
 * collection content across builds. This holds for artifact transform outputs that are in the file
 * collection. In fact, sorting it means that artifact transform outputs for library projects will
 * not be consistent across builds. See http://b/119064593#comment11 for details.
 */
private fun getAllRegularFiles(files: Set<File>): List<File> {
    return files.flatMap {
        if (it.isFile) listOf(it)
        else {
            it.walkTopDown()
                .filter { it.isFile }
                .sortedWith(
                    Comparator { left, right ->
                        val systemIndependentLeft = toSystemIndependentPath(left.toPath())
                        val systemIndependentRight = toSystemIndependentPath(right.toPath())
                        systemIndependentLeft.compareTo(systemIndependentRight)
                    }
                )
                .toList()
        }
    }
}

/**
 * Native multidex mode on android L does not support more
 * than 100 DEX files (see <a href="http://b.android.com/233093">http://b.android.com/233093</a>).
 *
 * We assume the maximum number of dexes that will be produced from the external dependencies and
 * project dex files is 50. The remaining 50 dex file can be used for library project.
 *
 * This means that if the number of library dex files is 51+, we might merge all of them when minSdk
 * is 21 or 22.
 */
internal const val LIBRARIES_MERGING_THRESHOLD = 51

enum class DexMergingAction {
    /** Merge only external libraries' dex files. */
    MERGE_EXTERNAL_LIBS,
    /** Merge only library projects' dex files. */
    MERGE_LIBRARY_PROJECTS,
    /** Merge only project's dex files. */
    MERGE_PROJECT,
    /** Merge external libraries, library projects, and project dex files. */
    MERGE_ALL,
}

/** Delegate for [DexMergingTask]. It contains all logic for merging dex files. */
@VisibleForTesting
class DexMergingTaskRunnable @Inject constructor(
    private val params: DexMergingParams
) : Runnable {

    override fun run() {
        val logger = LoggerWrapper.getLogger(DexMergingTaskRunnable::class.java)
        val messageReceiver = MessageReceiverImpl(
            params.errorFormatMode,
            Logging.getLogger(DexMergingTask::class.java)
        )
        val forkJoinPool = ForkJoinPool()

        val outputHandler = ParsingProcessOutputHandler(
            ToolOutputParser(DexParser(), Message.Kind.ERROR, logger),
            ToolOutputParser(DexParser(), logger),
            messageReceiver
        )

        var processOutput: ProcessOutput? = null
        try {
            processOutput = outputHandler.createOutput()
            FileUtils.cleanOutputDir(params.outputDir)

            if (params.dexFiles.isEmpty()) {
                return
            }

            if (params.dexFiles.size >= params.mergingThreshold) {
                DexMergerTransformCallable(
                    messageReceiver,
                    params.dexingType,
                    processOutput,
                    params.outputDir,
                    params.dexFiles.map { it.toPath() }.iterator(),
                    params.mainDexListFile?.toPath(),
                    forkJoinPool,
                    params.dexMerger,
                    params.minSdkVersion,
                    params.isDebuggable
                ).call()
            } else {
                for (file in getAllRegularFiles(params.dexFiles).withIndex()) {
                    file.value.copyTo(params.outputDir.resolve("classes_${file.index}.${SdkConstants.EXT_DEX}"))
                }
            }
        } catch (e: Exception) {
            PluginCrashReporter.maybeReportException(e)
            // Print the error always, even without --stacktrace
            logger.error(null, Throwables.getStackTraceAsString(e))
            throw TransformException(e)
        } finally {
            processOutput?.let {
                try {
                    outputHandler.handleOutput(it)
                    processOutput.close()
                } catch (ignored: ProcessException) {
                }
            }
            forkJoinPool.shutdown()
            forkJoinPool.awaitTermination(100, TimeUnit.SECONDS)
        }
    }
}

@VisibleForTesting
data class DexMergingParams(
    val dexingType: DexingType,
    val errorFormatMode: SyncOptions.ErrorFormatMode,
    val dexMerger: DexMergerTool,
    val minSdkVersion: Int,
    val isDebuggable: Boolean,
    val mergingThreshold: Int,
    val mainDexListFile: File?,
    val dexFiles: Set<File>,
    val outputDir: File
) : Serializable