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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactKind
import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ArtifactKind.DIRECTORY
import com.android.build.api.artifact.ArtifactType
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation

/**
 * List of internal [Artifact.Multiple] [Artifact]
 */
sealed class InternalMultipleArtifactType(
    category: Category = Category.INTERMEDIATES
) : Artifact.MultipleArtifact<Directory>(DIRECTORY, category), Artifact.Appendable {

    // The final dex files (if the dex splitter does not run)
    // that will get packaged in the APK or bundle.
    object DEX: InternalMultipleArtifactType()

    // External libraries' dex files only.
    object EXTERNAL_LIBS_DEX: InternalMultipleArtifactType()

    // Partial R.txt files generated by AAPT2 at compile time.
    object PARTIAL_R_FILES: InternalMultipleArtifactType()

    // --- Namespaced android res ---
    // Compiled resources (directory of .flat files) for the local library
    object RES_COMPILED_FLAT_FILES: InternalMultipleArtifactType()
}