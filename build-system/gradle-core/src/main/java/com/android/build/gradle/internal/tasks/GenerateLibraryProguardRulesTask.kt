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
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.gradle.internal.scope.ExistingBuildElements
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.getChangesInSerializableForm
import com.android.builder.files.SerializableChange
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.symbols.generateKeepRulesFromLayoutXmlFile
import com.android.ide.common.symbols.generateMinifyKeepRules
import com.android.ide.common.symbols.parseManifest
import com.android.ide.common.symbols.parseMinifiedKeepRules
import com.android.resources.ResourceFolderType
import com.google.common.collect.Iterables
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.Serializable
import java.nio.file.Files
import javax.inject.Inject
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
abstract class GenerateLibraryProguardRulesTask : NewIncrementalTask() {

    @get:OutputFile
    abstract val proguardOutputFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: DirectoryProperty

    @get:Incremental
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputResourcesDir: DirectoryProperty

    override fun doTaskAction(inputChanges: InputChanges) {
        val isIncremental = inputChanges.isIncremental
        val manifest = Iterables.getOnlyElement(
          ExistingBuildElements.from(InternalArtifactType.MERGED_MANIFESTS, manifestFiles))
          .outputFile
        val changedResources = if (isIncremental) {
            inputChanges.getChangesInSerializableForm(inputResourcesDir).changes
        } else {
            emptyList()
        }
        getWorkerFacadeWithWorkers().use {
            it.submit(
              GenerateProguardRulesRunnable::class.java,
              GenerateProguardRulesParams(
                manifestFile = manifest,
                proguardOutputFile = proguardOutputFile.get().asFile,
                inputResourcesDir = inputResourcesDir.get().asFile,
                changedResources = changedResources,
                isIncremental = isIncremental
              ))
        }
    }

    data class GenerateProguardRulesParams(
        val manifestFile: File,
        val proguardOutputFile: File,
        val inputResourcesDir: File,
        val changedResources: Collection<SerializableChange>,
        val isIncremental: Boolean
    ): Serializable

    class GenerateProguardRulesRunnable @Inject constructor(
        private val params: GenerateProguardRulesParams
    ): Runnable {
        override fun run() {
            if (canBeProcessedIncrementally(params)) {
                runIncrementalTask(params)
                return
            }
            runFullTask(params)
        }

        private fun canBeProcessedIncrementally(params: GenerateProguardRulesParams): Boolean =
            params.changedResources.all { canResourcesBeProcessedIncrementally(it) }
            && params.isIncremental
    }

    class CreationAction(
        componentProperties: ComponentPropertiesImpl
    ): VariantTaskCreationAction<GenerateLibraryProguardRulesTask, ComponentPropertiesImpl>(
        componentProperties
    ) {

        override val name: String
            get() = computeTaskName("generate", "LibraryProguardRules")
        override val type: Class<GenerateLibraryProguardRulesTask>
            get() = GenerateLibraryProguardRulesTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<out GenerateLibraryProguardRulesTask>
        ) {
            super.handleProvider(taskProvider)

            component.artifacts.producesFile(
                InternalArtifactType.AAPT_PROGUARD_FILE,
                taskProvider,
                GenerateLibraryProguardRulesTask::proguardOutputFile,
                SdkConstants.FN_AAPT_RULES
            )
        }

        override fun configure(
            task: GenerateLibraryProguardRulesTask
        ) {
            super.configure(task)

            component.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.PACKAGED_RES,
                task.inputResourcesDir
            )

             component.artifacts.setTaskInputToFinalProduct(
                 InternalArtifactType.MERGED_MANIFESTS, task.manifestFiles)
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

internal fun runFullTask(params: GenerateLibraryProguardRulesTask.GenerateProguardRulesParams) {
    // Generate `aapt_rules.txt` containing keep rules for Proguard.
    Files.write(
      params.proguardOutputFile.toPath(),
      generateMinifyKeepRules(
        parseManifest(params.manifestFile), params.inputResourcesDir)
    )
}

internal fun runIncrementalTask(
  params: GenerateLibraryProguardRulesTask.GenerateProguardRulesParams) {
    if (!params.proguardOutputFile.exists()) {
        Logging.getLogger(GenerateLibraryProguardRulesTask::class.java)
          .warn("Cannot find file: ${params.proguardOutputFile.path}")
        runFullTask(params)
        return
    }
    val addedLayoutFiles = params.changedResources
      .filter {
          isLayoutFile(it.file) && it.fileStatus == FileStatus.NEW
      }
      .map {
          it.file
      }
    if (addedLayoutFiles.none()) {
        return
    }
    val currentKeepRules = parseMinifiedKeepRules(params.proguardOutputFile)
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    addedLayoutFiles.forEach { addedLayoutFile ->
        generateKeepRulesFromLayoutXmlFile(
          addedLayoutFile, documentBuilder, currentKeepRules)
    }
    val contentsToWrite =
      "# Generated by the gradle plugin\n${currentKeepRules.joinToString("\n")}"
        .toByteArray()
    Files.write(params.proguardOutputFile.toPath(), contentsToWrite)
}

private fun isLayoutFile(file: File): Boolean =
  ResourceFolderType.getFolderType(file.parentFile.name) == ResourceFolderType.LAYOUT
