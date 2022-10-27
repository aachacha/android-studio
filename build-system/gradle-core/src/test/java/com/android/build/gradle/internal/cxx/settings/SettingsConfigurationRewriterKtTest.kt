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
import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_BUILD_TYPE
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_CXX_FLAGS
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_LIBRARY_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_RUNTIME_OUTPUT_DIRECTORY
import com.android.build.gradle.internal.cxx.configure.CmakeProperty.CMAKE_TOOLCHAIN_FILE
import com.android.build.gradle.internal.cxx.configure.createInitialCxxModel
import com.android.build.gradle.internal.cxx.configure.getCmakeBinaryOutputPath
import com.android.build.gradle.internal.cxx.configure.getCmakeGenerator
import com.android.build.gradle.internal.cxx.configure.getCmakeProperty
import com.android.build.gradle.internal.cxx.configure.toCmakeArguments
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.model.BasicCmakeMock
import com.android.build.gradle.internal.cxx.model.CmakeSettingsMock
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.DIFFERENT_MOCK_CMAKE_SETTINGS_CONFIGURATION
import com.android.build.gradle.internal.cxx.model.createCxxAbiModel
import com.android.build.gradle.internal.cxx.model.createCxxVariantModel
import com.android.build.gradle.internal.cxx.model.getBuildCommandArguments
import com.android.build.gradle.internal.cxx.model.toJsonString
import com.android.build.gradle.internal.cxx.settings.Macro.ENV_THIS_FILE
import com.android.build.gradle.internal.cxx.settings.Macro.ENV_WORKSPACE_ROOT
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_FULL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_MODULE_NDK_DIR
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class SettingsConfigurationRewriterKtTest {
    @Test
    fun `values read from environments`() {
        CmakeSettingsMock().apply {
            RandomInstanceGenerator().cmakeSettingsJsons().forEach { settingsJson ->
                val cmakeSettingsFile = File(abi.resolveMacroValue(ENV_THIS_FILE))
                cmakeSettingsFile.writeText("""
                    {
                      "environments": [{
                        "environment": "ndk-setup",
                        "namespace": "ndkSetup",
                        "inheritEnvironments": ["ndk"],
                        "outputRoot": "${ENV_WORKSPACE_ROOT.ref}/.cxx/cmake/build",
                        "hashAbi": "${'$'}{ndk.configurationHash}/${'$'}{ndk.abi}"
                      }],
                      "configurations": [{
                        "name": "android-gradle-plugin-predetermined-name",
                        "description": "Configuration generated by Android Gradle Plugin",
                        "inheritEnvironments": ["ndk-setup"],
                        "buildRoot": "${'$'}{ndkSetup.outputRoot}/build/${'$'}{ndkSetup.hashAbi}",
                        "variables": [
                          {"name": "CMAKE_LIBRARY_OUTPUT_DIRECTORY", "value": "${'$'}{ndkSetup.outputRoot}/lib/${'$'}{ndkSetup.hashAbi}"}
                        ]
                      }]
                    }
                """.trimIndent())
                Mockito.doReturn(FakeGradleProvider(cmakeSettingsFile.readText())).`when`(fileContents).asText
                abi.toJsonString() // Force lazy fields to evaluate
                val rewritten = abi.calculateConfigurationArguments(providers, layout)
                rewritten.toJsonString() // Force lazy fields to evaluate
            }
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
            val rewritten = abi.calculateConfigurationArguments(providers, layout)
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
            val rewritten = abi
                .calculateConfigurationArguments(providers, layout)
            val variables = rewritten.configurationArguments.toCmakeArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeGenerator()).isEqualTo("Ninja")
            assertThat(variables.getCmakeProperty(CMAKE_LIBRARY_OUTPUT_DIRECTORY)).isEqualTo(rewritten.soFolder.absolutePath)
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
                Abi.X86).calculateConfigurationArguments(providers, layout)
            val variables = abi.configurationArguments.toCmakeArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeGenerator()).isEqualTo("some other generator")
            assertThat(variables.getCmakeProperty(CMAKE_LIBRARY_OUTPUT_DIRECTORY)?.replace('\\', '/'))
                .endsWith("MyProject/Source/Android/build/android/lib/MyCustomBuildType/x86")
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
            val model =
                    createInitialCxxModel(
                        sdkComponents,
                        listOf(configurationParameters),
                        providers,
                        layout
                    )
            val abi = model.single { it.abi == Abi.X86 }
            val variables = abi.configurationArguments.toCmakeArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("MinSizeRel")
        }
    }

    @Test
    fun `ndkBuild bug with duplicate build arguments`() {
        BasicCmakeMock().apply {
            val configurationParameters = configurationParameters.copy(
                    buildSystem = NativeBuildSystem.NDK_BUILD,
                    nativeVariantConfig = configurationParameters.nativeVariantConfig.copy(
                            arguments = listOf("NDK_MODULE_PATH+=./third_party/modules")
                    )

            )
            val abis =
                    createInitialCxxModel(
                        sdkComponents,
                        listOf(configurationParameters),
                        providers,
                        layout
                    )
            abis.forEach { abi ->
                abi.configurationArguments
                        .groupBy { it }
                        .forEach { (argument, arguments) ->
                            if (arguments.size > 1) {
                                error("Argument $argument is duplicated")
                            }
                        }
            }
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
                    Abi.X86).calculateConfigurationArguments(providers, layout)
            println(abi.toJsonString())
            assertThat(abi.configurationArguments).contains("-CD:\\Test\\TargetProperties.cmake")
            assertThat(abi.configurationArguments).contains("--log-level=VERBOSE")
            assertThat(abi.configurationArguments).contains("-X some-parameter-after-a-space")
        }
    }

    @Test
    fun `make sure ANDROID_STL can be extracted`() {
        CmakeSettingsMock().apply {
            val variant = variant.copy(
                    buildSystemArgumentList = listOf("-DANDROID_STL=c++_shared"),
                    )
            val abi = createCxxAbiModel(
                    sdkComponents,
                    configurationParameters,
                    variant,
                    Abi.X86).calculateConfigurationArguments(providers, layout)
            println(abi.toJsonString())
            assertThat(abi.variant.stlType).contains("c++_shared")
            assertThat(abi.stlLibraryFile!!.toString()).endsWith("libc++_shared.so")
            assertThat(abi.configurationArguments).contains("-DANDROID_STL=c++_shared")
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
                Abi.X86).calculateConfigurationArguments(providers, layout)
            val variables = abi.configurationArguments.toCmakeArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(abi.variant.optimizationTag).isEqualTo("PrecedenceCheckingBuildType")
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("PrecedenceCheckingBuildType")
            assertThat(variables.getCmakeGenerator()).isEqualTo("PrecedenceCheckingGenerator")
            assertThat(variables.getCmakeProperty(CMAKE_TOOLCHAIN_FILE)).isEqualTo("PrecedenceCheckingToolchainFile")
        }
    }

    @Test
    fun `check MinSizeRel affects soFolder`() {
        CmakeSettingsMock().apply {
            val variant = variant.copy(
                    buildSystemArgumentList =
                    listOf("-D$CMAKE_BUILD_TYPE=MinSizeRel")
            )
            val abi = createCxxAbiModel(
                    sdkComponents,
                    configurationParameters,
                    variant,
                    Abi.X86).calculateConfigurationArguments(providers, layout)
            val variables = abi.configurationArguments.toCmakeArguments()
            println(variables.joinToString("\n") { it.sourceArgument })
            assertThat(abi.variant.optimizationTag).isEqualTo("MinSizeRel")
            assertThat(variables.getCmakeProperty(CMAKE_BUILD_TYPE)).isEqualTo("MinSizeRel")
        }
    }

    @Test
    fun `ABI does not contribute to hash`() {
        val (abi1, abi2) = abisOf(Abi.X86, Abi.X86_64)

        val commands1 = abi1.configurationArguments.toCmakeArguments()
        val commands2 = abi2.configurationArguments.toCmakeArguments()

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

        val commands1 = abi1.configurationArguments.toCmakeArguments()
        val commands2 = abi2.configurationArguments.toCmakeArguments()
        val buildRoot1 = commands1.getCmakeBinaryOutputPath()
        val buildRoot2 = commands2.getCmakeBinaryOutputPath()

        assertThat(buildRoot1).isNotNull()
        assertThat(buildRoot2).isNotNull()

        assertThat(buildRoot1).isNotEqualTo(buildRoot2)
        assertThat(File(buildRoot1).parentFile).isNotEqualTo(File(buildRoot2).parentFile)
    }

    @Test
    fun `no macro values are left unexpanded after final rewrite`() {
        CmakeSettingsMock().apply {
            val abi = abi.calculateConfigurationArguments(providers, layout)
            abi.toJsonString().lines().forEach { line ->
                if (line.contains("\${")) {
                    error("Final rewritten ABI [$abi] still has unexpanded macro: $line")
                }
            }
        }
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

            val rewritten = abi.calculateConfigurationArguments(providers, layout)

            assertThat(abi.buildSettings.environmentVariables).isEqualTo(
                listOf(
                    EnvironmentVariable("NDK_ABI", "\${ndk.abi}"),
                    EnvironmentVariable("NDK_DIR", NDK_MODULE_NDK_DIR.ref)
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
            Mockito.doReturn(FakeGradleProvider(settings.readText())).`when`(fileContents).asText
            setup(this, 1)

            val configurationModel1 = tryCreateConfigurationParameters(
                    Mockito.mock(ProjectOptions::class.java),
                    variantImpl)!!
            val variant1 = createCxxVariantModel(configurationModel1, module)
            val result1 = createCxxAbiModel(
                sdkComponents, configurationModel1,
                variant1, abi1).calculateConfigurationArguments(providers, layout)
            result1.toJsonString() // Force all lazy values

            setup(this, 2)
            val configurationModel2 = tryCreateConfigurationParameters(
                Mockito.mock(ProjectOptions::class.java),
                variantImpl)!!
            val variant2 = createCxxVariantModel(configurationModel2, module)
            val result2 = createCxxAbiModel(
                sdkComponents, configurationModel2,
                variant2, abi2).calculateConfigurationArguments(providers, layout)
            result2.toJsonString() // Force all lazy values

            return Pair(result1, result2)
        }
    }
}
