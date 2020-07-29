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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.buildOutputFiles
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.*
import com.android.build.gradle.internal.cxx.settings.Macro.*
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.NativeAndroidProject
import com.android.utils.FileUtils.join
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A CMakeSettings.json test where C++ builds are shared between two variants.
 * Tests the use of CMakeSettings.json configuration hash to fold multiple identical settings
 * into the same build and lib folders.
 */
@RunWith(Parameterized::class)
class CmakeSettingsSharedBuildTest(cmakeVersionInDsl: String) {

    @Rule
    @JvmField
    var project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build()
        )
        // TODO(159233213) Turn to ON when release configuration is cacheable
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
        .setCmakeVersion(cmakeVersionInDsl)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        // TODO(tgeng): Cover v2
        .addGradleProperties(BooleanOption.ENABLE_V2_NATIVE_MODEL.propertyName + "=false")
        .create()


    companion object {
        @Parameterized.Parameters(name = "model = {0}")
        @JvmStatic
        fun data() = arrayOf(
            arrayOf("3.6.0"),
            arrayOf("3.10.2"))
    }

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            join(project.buildFile.parentFile, "CMakeSettings.json"),
            """
            {
                "configurations": [{
                    "name": "android-gradle-plugin-predetermined-name",
                    "description": "Configuration generated by Android Gradle Plugin",
                    "inheritEnvironments": ["ndk"],
                    "buildRoot": "${NDK_PROJECT_DIR.ref}/.cxx/cmake/build/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}",
                    "variables": [
                        {"name": "$CMAKE_LIBRARY_OUTPUT_DIRECTORY", "value": "${NDK_PROJECT_DIR.ref}/.cxx/cmake/lib/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"},
                        {"name": "$CMAKE_RUNTIME_OUTPUT_DIRECTORY", "value": "${NDK_PROJECT_DIR.ref}/.cxx/cmake/runtime/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"}
                    ]
                }]
            }""".trimIndent())

        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                    ndkVersion "$DEFAULT_NDK_SIDE_BY_SIDE_VERSION"
                    externalNativeBuild {
                      cmake {
                        path "CMakeLists.txt"
                      }
                    }
                    buildTypes {
                        release {}
                        debug {}
                        bdebug {}
                        cdebug {}
                        ddebug {}
                        fdebug {}
                        gdebug {}
                        hdebug {}
                        minSizeRel {}
                    }
                }

            """.trimIndent()
        )
    }

    @Test
    fun checkBuildFoldersRedirected() {
        project.execute("assemble")
        val model = project.model().fetch(NativeAndroidProject::class.java)
        val abiCount = 4
        val uniqueConfigurations = 3
        assertThat(model.buildOutputFiles().distinct())
            .hasSize(abiCount * uniqueConfigurations)
        assertThat(model).allBuildOutputsExist()
    }
}
