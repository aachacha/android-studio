/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.getChangesInSerializableForm
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.generateKeepRulesFromLayoutXmlFile
import com.android.ide.common.symbols.generateMinifyKeepRules
import com.android.ide.common.symbols.parseManifest
import com.android.ide.common.symbols.parseMinifiedKeepRules
import com.android.resources.ResourceFolderType
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Generates the Proguard keep rules file `aapt_rules.txt`. In the past this was generated by AAPT,
 * but since we don't call AAPT(2) for packaging library resources we are generating the file
 * ourselves.
 *
 * This task takes the merged manifest and the merged resources directory (local only, small merge),
 * collects information from them and generates the keep rules file.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.OPTIMIZATION)
abstract class GenerateLibraryProguardRulesTask : NewIncrementalTask() {

    @get:OutputFile
    abstract val proguardOutputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputResourcesDir: DirectoryProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        val isIncremental = inputChanges.isIncremental
        val manifest = manifestFile.get().asFile
        if (!manifest.exists()) throw RuntimeException("Cannot find manifest file")
        val changedResources = if (isIncremental) {
            inputChanges.getChangesInSerializableForm(inputResourcesDir).changes
        } else {
            emptyList()
        }
        workerExecutor.noIsolation().submit(
            GenerateProguardRulesWorkAction::class.java
        ) {
            it.initializeFromAndroidVariantTask(this)
            it.manifestFile.set(manifest)
            it.proguardOutputFile.set(proguardOutputFile)
            it.inputResourcesDir.set(inputResourcesDir)
            it.changedResources.set(changedResources)
            it.incremental.set(inputChanges.isIncremental)
        }
    }

    abstract class GenerateProguardRulesWorkAction
        : ProfileAwareWorkAction<GenerateProguardRulesWorkAction.Params>()
    {
        abstract class Params : ProfileAwareWorkAction.Parameters() {
            abstract val manifestFile: RegularFileProperty
            abstract val proguardOutputFile: RegularFileProperty
            abstract val inputResourcesDir: DirectoryProperty
            abstract val changedResources: ListProperty<SerializableChange>
            abstract val incremental: Property<Boolean>
        }

        override fun run() {
            if (canBeProcessedIncrementally(parameters)) {
                runIncrementalTask(parameters)
                return
            }
            runFullTask(parameters)
        }

        private fun canBeProcessedIncrementally(params: Params): Boolean =
            params.changedResources.get().all { canResourcesBeProcessedIncrementally(it) }
                    && params.incremental.get()
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ): VariantTaskCreationAction<GenerateLibraryProguardRulesTask, ComponentCreationConfig>(
        creationConfig
    ) {

        override val name: String
            get() = computeTaskName("generate", "LibraryProguardRules")
        override val type: Class<GenerateLibraryProguardRulesTask>
            get() = GenerateLibraryProguardRulesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<GenerateLibraryProguardRulesTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                GenerateLibraryProguardRulesTask::proguardOutputFile
            ).withName(SdkConstants.FN_AAPT_RULES).on(InternalArtifactType.AAPT_PROGUARD_FILE)
        }

        override fun configure(
            task: GenerateLibraryProguardRulesTask
        ) {
            super.configure(task)

            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.inputResourcesDir
            )

             creationConfig.artifacts.setTaskInputToFinalProduct(
                 SingleArtifact.MERGED_MANIFEST, task.manifestFile)
        }
    }
}

internal fun canResourcesBeProcessedIncrementally(resourceChanges: SerializableChange): Boolean =
  when (resourceChanges.fileStatus) {
      FileStatus.NEW -> true
      FileStatus.CHANGED -> !isLayoutFile(resourceChanges.file)
      FileStatus.REMOVED -> !isLayoutFile(resourceChanges.file)
      else -> false
  }

internal fun runFullTask(
    params: GenerateLibraryProguardRulesTask.GenerateProguardRulesWorkAction.Params) {
    // Generate `aapt_rules.txt` containing keep rules for Proguard.
    Files.write(
      params.proguardOutputFile.get().asFile.toPath(),
      generateMinifyKeepRules(
        parseManifest(params.manifestFile.get().asFile), params.inputResourcesDir.get().asFile)
    )
}

internal fun runIncrementalTask(
  params: GenerateLibraryProguardRulesTask.GenerateProguardRulesWorkAction.Params) {
    if (!params.proguardOutputFile.get().asFile.exists()) {
        Logging.getLogger(GenerateLibraryProguardRulesTask::class.java)
          .warn("Cannot find file: ${params.proguardOutputFile.get().asFile.path}")
        runFullTask(params)
        return
    }
    val addedLayoutFiles = params.changedResources.get()
      .filter {
          isLayoutFile(it.file) && it.fileStatus == FileStatus.NEW
      }
      .map {
          it.file
      }
    if (addedLayoutFiles.none()) {
        return
    }
    val currentKeepRules = parseMinifiedKeepRules(params.proguardOutputFile.get().asFile)
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    addedLayoutFiles.forEach { addedLayoutFile ->
        generateKeepRulesFromLayoutXmlFile(
          addedLayoutFile, documentBuilder, currentKeepRules)
    }
    val contentsToWrite =
      "# Generated by the gradle plugin\n${currentKeepRules.joinToString("\n")}"
        .toByteArray()
    Files.write(params.proguardOutputFile.get().asFile.toPath(), contentsToWrite)
}

private fun isLayoutFile(file: File): Boolean =
  ResourceFolderType.getFolderType(file.parentFile.name) == ResourceFolderType.LAYOUT
