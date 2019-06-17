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

package com.android.build.gradle.tasks

import android.databinding.tool.DataBindingBuilder
import com.android.SdkConstants
import com.android.build.gradle.internal.scope.BuildArtifactsHolder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip

/** Custom Zip task to allow archive name to be set lazily. */
abstract class BundleAar : Zip(), VariantAwareTask {

    @Internal
    override lateinit var variantName: String

    class CreationAction(
        variantScope: VariantScope
    ) : VariantTaskCreationAction<BundleAar>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("bundle", "Aar")
        override val type: Class<BundleAar>
            get() = BundleAar::class.java

        override fun handleProvider(taskProvider: TaskProvider<out BundleAar>) {
            super.handleProvider(taskProvider)
            variantScope.taskContainer.bundleLibraryTask = taskProvider
            variantScope.artifacts.producesDir(InternalArtifactType.AAR,
                BuildArtifactsHolder.OperationType.INITIAL,
                taskProvider,
                BundleAar::getDestinationDirectory,
                variantScope.aarLocation.absolutePath,
                "")
        }

        override fun configure(task: BundleAar) {
            super.configure(task)

            val artifacts = variantScope.artifacts

            // Sanity check, there should never be duplicates.
            task.duplicatesStrategy = DuplicatesStrategy.FAIL
            // Make the AAR reproducible. Note that we package several zips inside the AAR, so all of
            // those need to be reproducible too before we can switch this on.
            // https://issuetracker.google.com/67597902
            task.isReproducibleFileOrder = true
            task.isPreserveFileTimestamps = false

            task.description = ("Assembles a bundle containing the library in "
                    + variantScope.variantConfiguration.fullName
                    + ".")

            task.archiveFileName.set(variantScope.outputScope.mainSplit.outputFileName)
            task.archiveExtension.set(BuilderConstants.EXT_LIB_ARCHIVE)
            task.from(
                variantScope.artifacts.getFinalProduct<Directory>(
                    InternalArtifactType.AIDL_PARCELABLE
                ),
                prependToCopyPath(SdkConstants.FD_AIDL)
            )
            task.from(artifacts.getFinalProduct<RegularFile>(
                InternalArtifactType.CONSUMER_PROGUARD_FILE))
            if (variantScope.globalScope.extension.dataBinding.isEnabled) {
                task.from(
                    variantScope.globalScope.project.provider {
                        variantScope.artifacts.getFinalProduct<Directory>(
                            InternalArtifactType.DATA_BINDING_ARTIFACT) },
                    prependToCopyPath(DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR)
                )
                task.from(
                    variantScope.artifacts.getFinalProduct<Directory>(
                        InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
                    prependToCopyPath(
                        DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR
                    )
                )
            }

            if (!variantScope.globalScope.extension.aaptOptions.namespaced) {
                // TODO: this should be unconditional b/69358522
                task.from(artifacts.getFinalProduct<RegularFile>(InternalArtifactType.SYMBOL_LIST))
                task.from(
                    artifacts.getFinalProduct<Directory>(InternalArtifactType.PACKAGED_RES),
                    prependToCopyPath(SdkConstants.FD_RES)
                )
                // In non-namespaced projects bundle the library manifest straight to the AAR.
                task.from(artifacts.getFinalProduct<Directory>(InternalArtifactType.LIBRARY_MANIFEST))
            } else {
                // In namespaced projects the bundled manifest needs to have stripped resource
                // references for backwards compatibility.
                task.from(artifacts.getFinalProduct<RegularFile>(
                    InternalArtifactType.NON_NAMESPACED_LIBRARY_MANIFEST))
            }
            task.from(
                artifacts.getFinalProduct<Directory>(InternalArtifactType.RENDERSCRIPT_HEADERS),
                prependToCopyPath(SdkConstants.FD_RENDERSCRIPT)
            )
            task.from(artifacts.getFinalProduct<RegularFile>(InternalArtifactType.PUBLIC_RES))
            if (artifacts.hasFinalProduct(InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR)) {
                task.from(artifacts.getFinalProduct<RegularFile>(
                    InternalArtifactType.COMPILE_ONLY_NAMESPACED_R_CLASS_JAR))
            }
            task.from(artifacts.getFinalProductAsFileCollection(InternalArtifactType.RES_STATIC_LIBRARY))
            task.from(
                artifacts.getFinalProduct<Directory>(LIBRARY_AND_LOCAL_JARS_JNI),
                prependToCopyPath(SdkConstants.FD_JNI)
            )
            task.from(variantScope.globalScope.artifacts
                .getFinalProduct<RegularFile>(InternalArtifactType.LINT_PUBLISH_JAR))
            task.from(artifacts.getFinalProduct<RegularFile>(InternalArtifactType.ANNOTATIONS_ZIP))
            task.from(artifacts.getFinalProduct<RegularFile>(InternalArtifactType.AAR_MAIN_JAR))
            task.from(
                artifacts.getFinalProduct<Directory>(InternalArtifactType.AAR_LIBS_DIRECTORY),
                prependToCopyPath(SdkConstants.LIBS_FOLDER)
            )
            task.from(
                variantScope.artifacts
                    .getFinalProduct<Directory>(InternalArtifactType.LIBRARY_ASSETS),
                prependToCopyPath(SdkConstants.FD_ASSETS))
        }

        private fun prependToCopyPath(pathSegment: String) = Action { copySpec: CopySpec ->
            copySpec.eachFile { fileCopyDetails: FileCopyDetails ->
                fileCopyDetails.relativePath =
                        fileCopyDetails.relativePath.prepend(pathSegment)
            }
        }
    }
}
