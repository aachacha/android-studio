/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class KotlinMultiplatformAndroidPluginBasicTest {

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Test
    fun applyShouldFailIfAnotherAndroidPluginHasBeenAppliedBefore() {
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                id("com.android.kotlin.multiplatform.library")
            """.trimIndent(),
            """
                id("com.android.library")
                id("com.android.kotlin.multiplatform.library")
            """.trimIndent()
        )

        val result =
            executor()
                .expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        result.assertErrorContains(
            "'com.android.kotlin.multiplatform.library' and 'com.android.library' plugins cannot be applied in the same project."
        )
    }

    @Test
    fun creatingArbitraryCompilationShouldFail() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        compilations.create("release")
                    }
                }
            """.trimIndent()
        )

        val result =
            executor()
                .expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        Truth.assertThat(result.failureMessage).contains(
            "Kotlin multiplatform android plugin doesn't support creating arbitrary compilations."
        )
    }

    @Test
    fun creatingTwoUnitTestCompilationsShouldFail() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        withAndroidTestOnJvm { }
                    }
                }
            """.trimIndent()
        )

        val result =
            executor()
                .expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        Truth.assertThat(result.failureMessage).contains(
            "Android tests on jvm has already been enabled, and a corresponding compilation (`unitTest`) has already been created."
        )
    }

    @Test
    fun `accessing predefined compilations should succeed`() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        afterEvaluate {
                            compilations {
                                val main by getting {
                                }
                                val unitTest by getting {
                                }
                                val instrumentedTest by getting {
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        executor()
            .run(":kmpFirstLib:androidPrebuild")
    }

    @Test
    fun kmpWithAndroidTestOnly() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpSecondLib").ktsBuildFile,
            """
                kotlin {
                    androidLibrary {
                        withAndroidTestOnDeviceBuilder {
                            compilationName = "instrumentedTest"
                            defaultSourceSetName = "androidInstrumentedTest"
                        }
                    }
                }
            """.trimIndent()
        )

        executor()
            .run(":kmpSecondLib:androidPrebuild")
    }

    @Test
    fun instrumentedTestAndroidManifestNotRequired() {
        val manifest = FileUtils.join(
            project.getSubproject("kmpFirstLib").projectDir,
            "src",
            "androidInstrumentedTest",
            "AndroidManifest.xml"
        ).toPath()

        val deleted = Files.deleteIfExists(manifest)
        Truth.assertThat(deleted).isTrue()
        val result = executor().run(":kmpFirstLib:packageAndroidInstrumentedTest")

        ScannerSubject.assertThat(result.stderr).doesNotContain(
            "Manifest file does not exist"
        )
    }

    private fun executor(): GradleTaskExecutor {
        return project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
    }
}
