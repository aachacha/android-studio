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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.BasicNdkBuildMock
import com.android.testutils.GoldenFile
import org.junit.Test
import com.android.SdkConstants.NDK_DEFAULT_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NDK_VERSION_MAJOR
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NDK_VERSION_MINOR

/**
 * Print CMakeSettings.json macros along with description and examples.
 *
 * The expected result file can be updated by running [SettingsJsonPropertiesGoldenFileUpdater.main]
 */
class SettingsJsonPropertiesGoldenFileTest {

    @Test
    fun validate() {
        try {
            goldenFile.assertUpToDate(updater = SettingsJsonPropertiesGoldenFileUpdater::class.java)
        } catch (e : AssertionError) {
            // Regenerate the baseline
            SettingsJsonPropertiesGoldenFileUpdater.main(arrayOf())
            throw e
        }
    }

    companion object {
        internal val goldenFile = GoldenFile(
            resourceRootWorkspacePath = "tools/base/build-system/gradle-core/src/test/resources",
            resourcePath = "com/android/build/gradle/internal/cxx/settings/CMakeSettingsJsonHostProperties.md",
            actualCallable = {
                val result = mutableListOf<String>()
                result += "This file generated by ${SettingsJsonPropertiesGoldenFileUpdater::class.java}"
                result += ""

                val ndkBuildExamples = mutableMapOf<Macro, String>()
                BasicNdkBuildMock().let {
                    // Walk all vals in the ndk-build model and record the ndk-build example if it
                    // exists.
                    Macro.values()
                        .forEach { macro ->
                            val ndkBuildExample = macro.ndkBuildExample
                                ?.replace('\\', '/')
                                ?.replace(".exe", "")
                            if (ndkBuildExample != null && ndkBuildExample.isNotBlank()) {
                                ndkBuildExamples[macro] = ndkBuildExample
                            }
                        }
                }
                BasicCmakeMock().let {
                    // Walk all vals in the model and invoke them
                    Macro.values()
                        .toList()
                        .sortedBy { macro -> macro.qualifiedName }
                        .forEach { macro ->
                            val example = macro.example
                                .replace(DEFAULT_CMAKE_VERSION, "[Current CMake Version]")
                                .replace(NDK_DEFAULT_VERSION, "[Current NDK Version]")
                                .replace('\\', '/')
                                .replace(".exe", "")

                            val ndkBuildExample = ndkBuildExamples[macro]
                            result += "## " + macro.ref
                            result += macro.description

                            // Hard code NDK major/minor version so the baseline doesn't change
                            // when NDK version is updated
                            if (macro == NDK_MODULE_NDK_VERSION_MAJOR) {
                                result += "- example: 21"
                            } else if (macro == NDK_MODULE_NDK_VERSION_MINOR) {
                                result += "- example: 4"
                            } else if (ndkBuildExample == null) {
                                if (example.isNotBlank()) {
                                    result += "- example: $example"
                                }
                            } else {
                                if (example.isNotBlank()) {
                                    result += "- cmake example: $example"
                                }
                                if (ndkBuildExample.isNotBlank()) {
                                    result += "- ndk-build example: $ndkBuildExample"
                                }
                            }
                            result += "- environment: ${macro.environment.environment}"
                            result += ""
                    }
                }
                result
            })
    }
}
