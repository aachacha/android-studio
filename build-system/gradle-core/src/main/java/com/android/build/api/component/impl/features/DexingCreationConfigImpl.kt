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

package com.android.build.api.component.impl.features

import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.DynamicFeatureCreationConfig
import com.android.build.gradle.internal.component.features.DexingCreationConfig
import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import com.android.build.gradle.internal.core.dsl.impl.computeMergedOptions
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.dexing.DexingType
import com.android.builder.errors.IssueReporter
import java.io.File
import kotlin.math.max

class DexingCreationConfigImpl(
    private val component: ApkCreationConfig,
    private val dslInfo: DexingDslInfo,
    private val internalServices: VariantServices,
    private val taskCreationServices: TaskCreationServices,
): DexingCreationConfig {

    init {

        dslInfo.multiDexKeepProguard?.let {
            // register the file in the global scope for the project as several variants might
            // be registering the same file. This would create a Gradle validation error as multiple
            // tasks would be producing the same file.
            val item = internalServices.projectInfo.projectDirectory.file(it.absolutePath)
            component.global.globalArtifacts
                .addStaticProvider(
                    component.artifacts
                        .getArtifactContainer(MultipleArtifact.MULTIDEX_KEEP_PROGUARD),
                    MultipleArtifact.MULTIDEX_KEEP_PROGUARD,
                    item = item
                )
        }
    }

    override val multiDexKeepFile: File?
        get() = dslInfo.multiDexKeepFile

    override val isMultiDexEnabled: Boolean
        get() {
            return dslInfo.isMultiDexEnabled ?: (component.minSdk.getFeatureLevel() >= 21)
        }

    override val needsMainDexListForBundle: Boolean
        get() = component.componentType.isBaseModule
                && component.global.hasDynamicFeatures
                && dexingType.needsMainDexList

    /**
     * Package desugar_lib DEX for base feature androidTest only if the base packages shrunk
     * desugar_lib. This should be fixed properly by analyzing the test code when generating L8
     * keep rules, and thus packaging desugar_lib dex in the tested APK only which contains
     * necessary classes used both in test and tested APKs.
     */
    override val shouldPackageDesugarLibDex: Boolean
        get() = when (component) {
            is AndroidTestCreationConfig -> {
                when {
                    !isCoreLibraryDesugaringEnabled -> false
                    component.mainVariant.componentType.isAar -> true
                    else -> component.mainVariant.componentType.isBaseModule && needsShrinkDesugarLibrary
                }
            }
            is DynamicFeatureCreationConfig -> false
            else -> isCoreLibraryDesugaringEnabled
        }

    override val dexingType: DexingType
        get() =
            if (component is DynamicFeatureCreationConfig) {
                // dynamic features can always be build in native multidex mode
                DexingType.NATIVE_MULTIDEX
            } else if (isMultiDexEnabled) {
                if (component.minSdk.getFeatureLevel() >= 21 ||
                    component.global.targetDeployApiFromIDE?.let { it >= 21 } == true
                ) {
                    // if minSdkVersion is 21+ or we are deploying to 21+ device, use native multidex
                    DexingType.NATIVE_MULTIDEX
                } else DexingType.LEGACY_MULTIDEX
            } else {
                DexingType.MONO_DEX
            }

    override val java8LangSupportType: Java8LangSupport
        get() {
            // in order of precedence
            return if (!component.global.compileOptions.targetCompatibility.isJava8Compatible) {
                Java8LangSupport.UNUSED
            } else if (component.services.projectInfo.hasPlugin("me.tatarka.retrolambda")) {
                Java8LangSupport.RETROLAMBDA
            } else if (component.optimizationCreationConfig.minifiedEnabled) {
                Java8LangSupport.R8
            } else {
                // D8 cannot be used if R8 is used
                Java8LangSupport.D8
            }
        }

    override val needsShrinkDesugarLibrary: Boolean
        get() {
            if (!isCoreLibraryDesugaringEnabled) {
                return false
            }
            // Assume Java8LangSupport is either D8 or R8 as we checked that in
            // isCoreLibraryDesugaringEnabled()
            return !(java8LangSupportType == Java8LangSupport.D8 && component.debuggable)
        }

    /**
     * Returns if core library desugaring is enabled.
     *
     * Java language desugaring and multidex are required for enabling core library desugaring.
     */
    override val isCoreLibraryDesugaringEnabled: Boolean
        get() {
            if (component is AndroidTestCreationConfig &&
                component.services.projectOptions.get(
                    BooleanOption.ENABLE_INSTRUMENTATION_TEST_DESUGARING
                )) {
                return true
            }
            val libDesugarEnabled = component.global.compileOptions.isCoreLibraryDesugaringEnabled
            val langSupportType = java8LangSupportType
            val langDesugarEnabled = langSupportType == Java8LangSupport.D8 ||
                    langSupportType == Java8LangSupport.R8
            if (libDesugarEnabled && !langDesugarEnabled) {
                component
                    .services
                    .issueReporter
                    .reportError(
                        IssueReporter.Type.GENERIC, "In order to use core library desugaring, "
                                + "please enable java 8 language desugaring with D8 or R8.")
            }
            if (libDesugarEnabled && !isMultiDexEnabled) {
                component
                    .services
                    .issueReporter
                    .reportError(
                        IssueReporter.Type.GENERIC, "In order to use core library desugaring, "
                                + "please enable multidex.")
            }
            return libDesugarEnabled
        }

    /**
     * Returns the minimum SDK version which we want to use for dexing.
     * In most cases this will be equal the minSdkVersion, but when the IDE is deploying to:
     * - device running API 24+, the min sdk version for dexing is max(24, minSdkVersion)
     * - device running API 23-, the min sdk version for dexing is minSdkVersion
     * - there is no device, the min sdk version for dexing is minSdkVersion
     * It is used to enable some optimizations to build the APK faster.
     *
     * This has no relation with targetSdkVersion from build.gradle/manifest.
     */
    override val minSdkVersionForDexing: AndroidVersion
        get() {
            val targetDeployApiFromIDE = component.global.targetDeployApiFromIDE ?: 1

            val minForDexing = if (targetDeployApiFromIDE >= com.android.sdklib.AndroidVersion.VersionCodes.N) {
                max(24, component.minSdk.getFeatureLevel())
            } else {
                component.minSdk.getFeatureLevel()
            }
            return AndroidVersionImpl(minForDexing)
        }
}
