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

package com.android.build.gradle.integration.projectisolation

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProjectIsolationWithAnalyticsTest {

    private val app = MinimalSubProject.app("com.app")
    private val lib = MinimalSubProject.lib("com.lib")

    @JvmField
    @Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib", lib)
                .subproject(
                    ":test",
                    MinimalSubProject
                        .test("com.test")
                        .appendToBuild("android.targetProjectPath ':app'")
                )
                .build()
        )
        .enableProfileOutput()
        .create()

    @Before
    fun setUp() {
        project.projectDir.resolve(".gradle/configuration-cache").deleteRecursively()
    }

    @Test
    fun testWithProjectIsolation() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.PROJECT_ISOLATION)
            // b/314817109
            .withArgument("-Dorg.gradle.unsafe.configuration-cache.max-problems=1")
            .run("assemble")
    }
}