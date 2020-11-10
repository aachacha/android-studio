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

import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_RUNTIME_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.createInitialCxxModel
import com.android.build.gradle.internal.cxx.configure.getCmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.getCmakeGenerator
import com.android.build.gradle.internal.cxx.configure.getCmakeProperty
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.CmakeSettingsMock
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.toJsonString
import com.android.build.gradle.internal.cxx.settings.Macro.ENV_WORKSPACE_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_FULL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NDK_DIR
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class CxxAbiModelCMakeSettingsRewriterKtTest {

    @Test
    fun `ensure traditional environment rewrite above ABI is nop`() {
        BasicCmakeMock().apply {
            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
            assertThat(abi.variant.toJsonString()).isEqualTo(rewritten.variant.toJsonString())
        }
    }

    @Test
    fun `check rewrite with CMakeSettings json`() {
        CmakeSettingsMock().apply {
            val variant = variant.copy(
                cmakeSettingsConfiguration = DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
            )
            val abi = createCxxAbiModel(
                sdkComponents,
                configurationParameters,
                variant,
                Abi.X86)
            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()
            assertThat(rewritten.cmake!!.effectiveConfiguration.generator).isEqualTo("some other generator")
            assertThat(rewritten.cxxBuildFolder.path).contains("some other build root folder")
            assertThat(rewritten.variant.module.cmake!!.cmakeExe!!.path
                .replace('\\', '/')).isEqualTo("my/path/to/cmake")
            assertThat(
                rewritten.variant.module.cmakeToolchainFile.path
                    .replace('\\', '/')
            ).isEqualTo("my/path/to/toolchain")
            assertThat(rewritten.getBuildCommandArguments()).containsExactly("-j", "100").inOrder()
        }
    }

    @Test
    fun `basic check`() {
        BasicCmakeMock().apply {
            val variables = abi
                .rewriteCxxAbiModelWithCMakeSettings()
                .getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeGenerator()).isEqualTo("Ninja")
            assertThat(variables.getCmakeProperty(CMAKE_LIBRARY_OUTPUT_DIRECTORY)).isEqualTo(abi.soFolder.absolutePath)
            assertThat(variables.getCmakeProperty(CMAKE_CXX_FLAGS)).isEqualTo("-DCPP_FLAG_DEFINED")
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("Debug")
        }
    }

    @Test
    fun `alternate check`() {
        CmakeSettingsMock().apply {
            val variant = variant.copy(
                cmakeSettingsConfiguration = DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
            )
            val abi = createCxxAbiModel(
                sdkComponents,
                configurationParameters,
                variant,
                Abi.X86).rewriteCxxAbiModelWithCMakeSettings()
            val variables = abi.getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeGenerator()).isEqualTo("some other generator")
            assertThat(variables.getCmakeProperty(CMAKE_LIBRARY_OUTPUT_DIRECTORY)?.replace('\\', '/'))
                .endsWith("MyProject/Source/Android/build/android/lib/Debug/x86")
            assertThat(variables.getCmakeProperty(CMAKE_CXX_FLAGS)).isEqualTo("-DTEST_CPP_FLAG")
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("MyCustomBuildType")
        }
    }

    @Test
    fun `map CMAKE_BUILD_TYPE to MinSizeRel`() {
        CmakeSettingsMock().apply {
            val configurationParameters = configurationParameters.copy(
                    variantName = "myMinSizeRel"
            )
            val model = createInitialCxxModel(sdkComponents, listOf(configurationParameters))
            val abi = model.single { it.abi == Abi.X86 }
            val variables = abi.getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("MinSizeRel")
        }
    }

    @Test
    fun `bug 159434435--unknown user build args are forwarded`() {
        CmakeSettingsMock().apply {
            val variant = variant.copy(
                    buildSystemArgumentList =
                        listOf(
                            "-CD:\\Test\\TargetProperties.cmake",
                            "--log-level=VERBOSE",
                            "-X some-parameter-after-a-space",
                        ),

            )
            val abi = createCxxAbiModel(
                    sdkComponents,
                    configurationParameters,
                    variant,
                    Abi.X86).rewriteCxxAbiModelWithCMakeSettings()
            println(abi.toJsonString())
            val variables = abi.getFinalCmakeCommandLineArguments().map { it.sourceArgument }
            assertThat(variables).contains("-CD:\\Test\\TargetProperties.cmake")
            assertThat(variables).contains("--log-level=VERBOSE")
            assertThat(variables).contains("-X some-parameter-after-a-space")
        }
    }

    @Test
    fun `user build args take precedence over default configuration`() {
        CmakeSettingsMock().apply {
            val variant = variant.copy(
                buildSystemArgumentList =
                    listOf("-GPrecedenceCheckingGenerator",
                        "-D$CMAKE_BUILD_TYPE=PrecedenceCheckingBuildType",
                        "-D$CMAKE_TOOLCHAIN_FILE=PrecedenceCheckingToolchainFile")
            )
            val abi = createCxxAbiModel(
                sdkComponents,
                configurationParameters,
                variant,
                Abi.X86).rewriteCxxAbiModelWithCMakeSettings()
            val variables = abi.getFinalCmakeCommandLineArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("PrecedenceCheckingBuildType")
            assertThat(variables.getCmakeGenerator()).isEqualTo("PrecedenceCheckingGenerator")
            assertThat(variables.getCmakeProperty(CMAKE_TOOLCHAIN_FILE)).isEqualTo("PrecedenceCheckingToolchainFile")
        }
    }

    @Test
    fun `ABI does not contribute to hash`() {
        val (abi1, abi2) = abisOf(Abi.X86, Abi.X86_64)

        val commands1 = abi1.getFinalCmakeCommandLineArguments()
        val commands2 = abi2.getFinalCmakeCommandLineArguments()

        assertThat(commands1.getCmakeBinaryOutputPath()).isNotNull()
        assertThat(commands2.getCmakeBinaryOutputPath()).isNotNull()

        assertThat(File(commands1.getCmakeBinaryOutputPath())).isNotEqualTo(File(commands2.getCmakeBinaryOutputPath()))
        assertThat(File(commands1.getCmakeBinaryOutputPath()).parentFile)
            .named("Comparing parent folders of ${commands1.getCmakeBinaryOutputPath()} and ${commands1.getCmakeBinaryOutputPath()}")
            .isEqualTo(File(commands2.getCmakeBinaryOutputPath()).parentFile)
    }

    @Test
    fun `configuration type build name does contribute to hash`() {
        val (abi1, abi2) = abisOf { mock, abi ->
            when (abi) {
                1 -> Mockito.doReturn("debug").`when`(mock.variantImpl).name
                2 -> Mockito.doReturn("release").`when`(mock.variantImpl).name
            }
        }

        val commands1 = abi1.getFinalCmakeCommandLineArguments()
        val commands2 = abi2.getFinalCmakeCommandLineArguments()
        val buildRoot1 = commands1.getCmakeBinaryOutputPath()
        val buildRoot2 = commands2.getCmakeBinaryOutputPath()

        assertThat(buildRoot1).isNotNull()
        assertThat(buildRoot2).isNotNull()

        assertThat(buildRoot1).isNotEqualTo(buildRoot2)
        assertThat(File(buildRoot1).parentFile).isNotEqualTo(File(buildRoot2).parentFile)
    }

    @Test
    fun `build settings macros are expanded`() {
        CmakeSettingsMock().apply {
            val buildSettingsJson = FileUtils.join(allPlatformsProjectRootDir, "BuildSettings.json")
            buildSettingsJson.writeText(
                """
                {
                    "environmentVariables": [
                        {
                            "name": "NDK_ABI",
                            "value": "${'$'}{ndk.abi}"
                        },
                        {
                            "name": "NDK_DIR",
                            "value": "${NDK_MODULE_NDK_DIR.ref}"
                        }
                    ]
                }
                """.trimIndent()
            )

            val rewritten = abi.rewriteCxxAbiModelWithCMakeSettings()

            assertThat(abi.buildSettings.environmentVariables).isEqualTo(
                listOf(
                    EnvironmentVariable("NDK_ABI", "\${ndk.abi}"),
                    EnvironmentVariable("NDK_DIR", "\${ndk.moduleNdkDir}")
                )
            )

            assertThat(rewritten.buildSettings.environmentVariables).isEqualTo(
                listOf(
                    EnvironmentVariable("NDK_ABI", abi.abi.tag),
                    EnvironmentVariable("NDK_DIR", abi.variant.module.ndkFolder.path)
                )
            )
        }
    }

    private fun abisOf(
        abi1 : Abi = Abi.X86,
        abi2 : Abi = Abi.X86,
        setup : (CmakeSettingsMock, Int) -> Unit = { _, _ -> }
    ) : Pair<CxxAbiModel, CxxAbiModel> {
        CmakeSettingsMock().apply {
            val moduleFolder = mockModule("app")
            Mockito.doReturn(FileUtils.join(moduleFolder, "CMakeLists.txt")).`when`(cmake).path
            val settings = FileUtils.join(moduleFolder, "CMakeSettings.json")
            settings.parentFile.mkdirs()
            settings.writeText(
                """{
                "configurations": [{
                    "name": "android-gradle-plugin-predetermined-name",
                    "description": "Configuration generated by Android Gradle Plugin",
                    "inheritEnvironments": ["ndk"],
                    "buildRoot": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/build/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}",
                    "cmakeCommandArgs": "-DFULL_HASH=${NDK_FULL_CONFIGURATION_HASH.ref}",
                    "variables": [
                        {"name": "$CMAKE_LIBRARY_OUTPUT_DIRECTORY",
                         "value": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/lib/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"},
                        {"name": "$CMAKE_RUNTIME_OUTPUT_DIRECTORY",
                         "value": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/runtime/${NDK_CONFIGURATION_HASH.ref}/${NDK_ABI.ref}"}
                    ]
                }]
                }""".trimIndent()
            )
            setup(this, 1)

            val configurationModel1 = tryCreateConfigurationParameters(
                    variantImpl)!!
            val variant1 = createCxxVariantModel(configurationModel1, module)
            val result1 = createCxxAbiModel(
                sdkComponents, configurationModel1,
                variant1, abi1).rewriteCxxAbiModelWithCMakeSettings()
            result1.toJsonString() // Force all lazy values

            setup(this, 2)
            val configurationModel2 = tryCreateConfigurationParameters(variantImpl)!!
            val variant2 = createCxxVariantModel(configurationModel2, module)
            val result2 = createCxxAbiModel(
                sdkComponents, configurationModel2,
                variant2, abi2).rewriteCxxAbiModelWithCMakeSettings()
            result2.toJsonString() // Force all lazy values

            return Pair(result1, result2)
        }
    }
}
