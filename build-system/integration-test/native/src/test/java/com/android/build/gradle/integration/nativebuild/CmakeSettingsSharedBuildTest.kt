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
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.dump
import com.android.build.gradle.integration.common.fixture.model.filterByVariantName
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_RUNTIME_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.DEFAULT_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.configure.OFF_STAGE_CMAKE_VERSION
import com.android.build.gradle.internal.cxx.settings.Macro.ENV_WORKSPACE_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.utils.FileUtils.join
import com.google.common.truth.Truth
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
// TODO(b/134757616): Enable when V2 models is supported. Currently since the build folder is
// shared, concurrently executing V2 sync fails since all compile_commands.json.bin are generated
// in the same location.
@RunWith(Parameterized::class)
class CmakeSettingsSharedBuildTest(cmakeVersionInDsl: String) {

    @Rule
    @JvmField
    var project = GradleTestProject.builder()
      .fromTestApp(
        HelloWorldJniApp.builder().withNativeDir("cxx").withCmake().build()
      )
      // TODO(b/159233213) Turn to ON when release configuration is cacheable
      .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
      .setCmakeVersion(cmakeVersionInDsl)
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()


    companion object {
        @Parameterized.Parameters(name = "model = {0}")
        @JvmStatic
        fun data() = arrayOf(
          arrayOf("3.6.0"),
          arrayOf(DEFAULT_CMAKE_VERSION),
          arrayOf(OFF_STAGE_CMAKE_VERSION)
        )
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
                    "buildRoot": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/build/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}",
                    "variables": [
                        {"name": "$CMAKE_LIBRARY_OUTPUT_DIRECTORY", "value": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/lib/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"},
                        {"name": "$CMAKE_RUNTIME_OUTPUT_DIRECTORY", "value": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/runtime/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"}
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
                        release2 {}
                        release3 {}
                        release4 {}
                        release5 {}
                        minSizeRel {}
                    }
                }

            """.trimIndent()
        )
    }

    @Test
    fun checkBuildFoldersRedirected() {
        val result =
          project.modelV2().fetchNativeModules(NativeModuleParams(listOf("debug", "release"), listOf("x86_64")))
        // There are a lot of variants, just peek at the first several lines
        val severalLines = result.dump(filterByVariantName("debug", "release"))
                .lines().take(40).joinToString("\n")
        Truth.assertThat(severalLines).isEqualTo("""[:]
> NativeModule:
   - name                    = "project"
   > variants:
      > debug:
         > abis:
            - arm64-v8a:
               - sourceFlagsFile                 = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/arm64-v8a/additional_project_files.txt{!}
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/armeabi-v7a/additional_project_files.txt{!}
            - x86:
               - sourceFlagsFile                 = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86/additional_project_files.txt{!}
            - x86_64:
               - sourceFlagsFile                 = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/compile_commands.json.bin{F}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/symbol_folder_index.txt{F}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/build_file_index.txt{F}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{DEBUG}/meta/x86_64/additional_project_files.txt{F}
         < abis
      < debug
      > release:
         > abis:
            - arm64-v8a:
               - sourceFlagsFile                 = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/arm64-v8a/additional_project_files.txt{!}
            - armeabi-v7a:
               - sourceFlagsFile                 = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/compile_commands.json.bin{!}
               - symbolFolderIndexFile           = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/symbol_folder_index.txt{!}
               - buildFileIndexFile              = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/build_file_index.txt{!}
               - additionalProjectFilesIndexFile = {PROJECT}/build/intermediates/{RELEASE}/meta/armeabi-v7a/additional_project_files.txt{!}""")
    }
}
