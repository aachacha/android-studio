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

package com.android.build.gradle.internal.tasks.databinding

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.IncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.ide.common.resources.FileStatus
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File

abstract class DataBindingMergeBaseClassLogTask: IncrementalTask() {

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var moduleClassLog: FileCollection
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    lateinit var externalClassLog: FileCollection
        private set

    private lateinit var delegate: DataBindingMergeBaseClassLogDelegate

    override val incremental: Boolean
        get() = true

    override fun doFullTaskAction() {
        delegate.doFullRun(getWorkerFacadeWithWorkers())
    }

    override fun doIncrementalTaskAction(changedInputs: Map<File, FileStatus>) {
        delegate.doIncrementalRun(getWorkerFacadeWithWorkers(), changedInputs)
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<DataBindingMergeBaseClassLogTask>(variantScope) {

        override val name = variantScope.getTaskName("dataBindingMergeGenClasses")
        override val type = DataBindingMergeBaseClassLogTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out DataBindingMergeBaseClassLogTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesDir(
                InternalArtifactType.DATA_BINDING_BASE_CLASS_LOGS_DEPENDENCY_ARTIFACTS,
                taskProvider,
                DataBindingMergeBaseClassLogTask::outFolder
            )
        }

        override fun configure(task: DataBindingMergeBaseClassLogTask) {
            super.configure(task)

            // data binding related artifacts for external libs
            task.moduleClassLog = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH,
                PROJECT,
                ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
            )

            task.externalClassLog = variantScope.getArtifactFileCollection(
                COMPILE_CLASSPATH,
                EXTERNAL,
                ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT
            )

            task.delegate = DataBindingMergeBaseClassLogDelegate(
                task.moduleClassLog,
                task.externalClassLog,
                task.outFolder)
        }
    }
}
