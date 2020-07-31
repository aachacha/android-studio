/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.testutils.truth.FileSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PrefabPublishingTest(
    private val variant: String,
    private val buildSystem: NativeBuildSystem
) {
    private val projectName = "prefabPublishing"
    private val gradleModuleName = "foo"

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject(projectName)
        .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    private val ndkMajor = GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION.split(".").first()

    private val expectedAbis = listOf(Abi.ARMEABI_V7A, Abi.ARM64_V8A, Abi.X86, Abi.X86_64)

    companion object {
        @Parameterized.Parameters(name = "variant = {0}, build system = {1}")
        @JvmStatic
        fun data() = listOf(
            arrayOf("debug", NativeBuildSystem.CMAKE),
            arrayOf("debug", NativeBuildSystem.NDK_BUILD),
            arrayOf("release", NativeBuildSystem.CMAKE),
            arrayOf("release", NativeBuildSystem.NDK_BUILD)
        )
    }

    private fun execute(vararg tasks: String) {
        when (buildSystem) {
            NativeBuildSystem.NDK_BUILD -> project.execute(mutableListOf("-PndkBuild"), *tasks)
            else -> project.execute(*tasks)
        }
    }

    private fun verifyModule(packageDir: File, moduleName: String) {
        val moduleDir = packageDir.resolve("modules/$moduleName")
        val moduleMetadata = moduleDir.resolve("module.json").readText()
        Truth.assertThat(moduleMetadata).isEqualTo(
            """
            {
              "export_libraries": [],
              "android": {}
            }
            """.trimIndent()
        )

        val header = moduleDir.resolve("include/$gradleModuleName/$gradleModuleName.h").readText()
        Truth.assertThat(header).isEqualTo(
            """
            #pragma once

            void $gradleModuleName();

            """.trimIndent()
        )

        for (abi in expectedAbis) {
            val abiDir = moduleDir.resolve("libs/android.${abi.tag}")
            val abiMetadata = abiDir.resolve("abi.json").readText()
            val apiLevel = if (abi.supports64Bits()) {
                21
            } else {
                16
            }

            Truth.assertThat(abiMetadata).isEqualTo(
                """
                {
                  "abi": "${abi.tag}",
                  "api": $apiLevel,
                  "ndk": $ndkMajor,
                  "stl": "c++_shared"
                }
                """.trimIndent()
            )

            val suffix = if (moduleName.endsWith("_static")) {
                ".a"
            } else {
                ".so"
            }
            val library = abiDir.resolve("lib$moduleName$suffix")
            assertThat(library).exists()
        }
    }

    @Test
    fun `project builds`() {
        execute("clean", "assemble$variant")
    }

    @Test
    fun `prefab package was constructed correctly`() {
        execute("assemble$variant")

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val packageMetadata = packageDir.resolve("prefab.json").readText()
        Truth.assertThat(packageMetadata).isEqualTo(
            """
            {
              "name": "$gradleModuleName",
              "schema_version": 1,
              "dependencies": [],
              "version": "1.0"
            }
            """.trimIndent()
        )

        for (suffix in listOf("", "_static")) {
            verifyModule(packageDir, "$gradleModuleName$suffix")
        }
    }

    @Test
    fun `AAR contains the prefab packages`() {
        execute("clean", "assemble$variant")
        project.getSubproject(gradleModuleName).assertThatAar(variant) {
            containsFile("prefab/prefab.json")
            containsFile("prefab/modules/$gradleModuleName/module.json")
            containsFile("prefab/modules/${gradleModuleName}_static/module.json")
        }
    }

    @Test
    fun `adding a new header causes a rebuild`() {
        execute("assemble${variant.toLowerCase()}")
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        assertThat(header).doesNotExist()

        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
                #pragma once
                void bar();
                """.trimIndent()
        )

        execute("assemble$variant")
        assertThat(header).exists()
    }

    @Test
    fun `removing a header causes a rebuild`() {
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
            #pragma once
            void bar();
            """.trimIndent()
        )

        execute("assemble$variant")
        assertThat(header).exists()

        headerSrc.delete()
        execute("assemble$variant")
        assertThat(header).doesNotExist()
    }

    @Test
    fun `changing a header causes a rebuild`() {
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
                #pragma once
                void bar();
                """.trimIndent()
        )

        execute("assemble$variant")
        assertThat(header).exists()

        val newHeaderContents = """
                #pragma once
                void bar(int);
                """.trimIndent()

        headerSrc.writeText(newHeaderContents)
        execute("assemble$variant")
        Truth.assertThat(header.readText()).isEqualTo(newHeaderContents)
    }
}