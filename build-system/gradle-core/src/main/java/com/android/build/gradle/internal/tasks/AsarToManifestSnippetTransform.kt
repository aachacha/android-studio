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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.privaysandboxsdk.tagAllElementsAsRequiredByPrivacySandboxSdk
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask
import com.android.bundle.SdkMetadataOuterClass
import com.android.ide.common.signing.KeystoreHelper
import com.android.tools.build.bundletool.model.RuntimeEnabledSdkVersionEncoder
import com.android.tools.build.bundletool.transparency.CodeTransparencyCryptoUtils
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipFile

@CacheableTransform
abstract class AsarToManifestSnippetTransform : TransformAction<AsarToManifestSnippetTransform.Parameters> {

    interface Parameters : GenericTransformParameters {

        @get:Nested
        val signingConfigData: Property<SigningConfigData>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val asar: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val asarFile = asar.get().asFile
        ZipFile(asarFile).use {
            val outputFile = outputs.file(asar.get().asFile.nameWithoutExtension
                    + SdkConstants.PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX)
                    .toPath()
            val block: (InputStream) -> Unit = { protoBytes ->
                val metadata = SdkMetadataOuterClass.SdkMetadata.parseFrom(protoBytes)
                val encodedVersion =
                        RuntimeEnabledSdkVersionEncoder.encodeSdkMajorAndMinorVersion(
                                metadata.sdkVersion.major,
                                metadata.sdkVersion.minor
                        )
                // use the certificate digest for the default debug keystore as this is for local
                // debugging only
                val signingConfigData = parameters.signingConfigData.get()
                val certInfo = KeystoreHelper.getCertificateInfo(
                        signingConfigData.storeType,
                        signingConfigData.storeFile,
                        signingConfigData.storePassword,
                        signingConfigData.keyPassword,
                        signingConfigData.keyAlias
                )
                val certificateDigest =
                        CodeTransparencyCryptoUtils.getCertificateFingerprint(certInfo.certificate).replace(' ', ':')
                outputFile.toFile().writeText(
                        "<manifest\n" +
                                "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                                "    <application>\n" +
                                "        <uses-sdk-library\n" +
                                "            android:name=\"" + metadata.packageName + "\"\n" +
                                "            android:certDigest=\"" + certificateDigest + "\"\n" +
                                "            android:versionMajor=\"" + encodedVersion + "\" />\n" +
                                "    </application>\n" +
                                "</manifest>\n" +
                                ""
                )
            }
            it.getInputStream(it.getEntry("SdkMetadata.pb")).use(block)
        }

    }
}


