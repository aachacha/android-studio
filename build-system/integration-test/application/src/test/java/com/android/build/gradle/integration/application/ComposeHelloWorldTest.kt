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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ComposeHelloWorldTest(private val useComposeCompilerGradlePlugin: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "useComposeCompilerGradlePlugin_{0}")
        fun parameters() = listOf(true, false)
    }

    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestProject("composeHelloWorld").create()

    @Before
    fun before() {
        if (!useComposeCompilerGradlePlugin) {
            TestFileUtils.searchAndReplace(
                project.buildFile,
                "kotlinVersion",
                "kotlinVersionForCompose"
            )
            TestFileUtils.searchAndReplace(
                project.buildFile,
                "classpath \"org.jetbrains.kotlin:compose-compiler-gradle-plugin",
                "// classpath \"org.jetbrains.kotlin:compose-compiler-gradle-plugin"
            )
            TestFileUtils.searchAndReplace(
                project.getSubproject("app").buildFile,
                "apply plugin: 'org.jetbrains.kotlin.plugin.compose'",
                ""
            )
            TestFileUtils.appendToFile(
                project.getSubproject("app").buildFile,
                """
                    android {
                        composeOptions {
                            kotlinCompilerExtensionVersion = "${"$"}{libs.versions.composeCompilerVersion.get()}"
                        }
                    }
                """.trimIndent()
            )
        }
    }

    @Test
    fun appAndTestsBuildSuccessfully() {
        val tasks = listOf("clean", "assembleDebug", "assembleDebugAndroidTest")
        project.executor().run(tasks)
        // run once again to test configuration caching
        project.executor().run(tasks)
    }

    @Test
    fun testLiveLiterals() {
        // Run compilation with live literals on
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            "android.composeOptions.useLiveLiterals = true"
        )
        project.executor().run("assembleDebug")

        // Turn off live literals and run again
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "android.composeOptions.useLiveLiterals = true",
            "android.composeOptions.useLiveLiterals = false"
        )
        val result = project.executor().run("assembleDebug")
        assertThat(result.didWorkTasks).contains(":app:compileDebugKotlin")
    }

    @Test
    fun testScreenshotTestAndTestFixturesCompilation() {
        project.executor().run(":app:compileDebugTestFixturesKotlin")
        val testFixturesClassFile =
            project.getSubproject("app")
                .getIntermediateFile(
                    "kotlinc",
                    "debugTestFixtures",
                    "compileDebugTestFixturesKotlin",
                    "classes",
                    "com",
                    "example",
                    "helloworldcompose",
                    "FixtureKt.class"
                )
        assertThat(testFixturesClassFile).exists()

        project.executor().run(":app:compileDebugScreenshotTestKotlin")
        val screenshotTestClassFile =
            project.getSubproject("app")
                .getIntermediateFile(
                    "kotlinc",
                    "debugScreenshotTest",
                    "compileDebugScreenshotTestKotlin",
                    "classes",
                    "com",
                    "example",
                    "helloworldcompose",
                    "ScreenshotTestKt.class"
                )
        assertThat(screenshotTestClassFile).exists()
    }

    @Test
    fun testErrorWhenComposeCompilerPluginNotAppliedWithKotlin2() {
        Assume.assumeTrue(useComposeCompilerGradlePlugin)
        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "apply plugin: 'org.jetbrains.kotlin.plugin.compose'",
            ""
        )
        val result = project.executor().expectFailure().run("assembleDebug")
        ScannerSubject.assertThat(result.stderr)
            .contains("Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required")
    }
}
