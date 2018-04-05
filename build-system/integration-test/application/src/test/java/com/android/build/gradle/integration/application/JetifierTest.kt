/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import com.google.common.base.Throwables
import org.gradle.tooling.BuildException
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * Integration test for the Jetifier feature.
 */
class JetifierTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("jetifier")
        .create()

    @Test
    fun testJetifierDisabled() {
        // Build the project with Jetifier disabled
        project.executor().with(BooleanOption.ENABLE_JETIFIER, false).run("assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // 1. Check that the old support library is not yet replaced with a new one
        assertThat(apk).containsClass("Landroid/support/v7/preference/Preference;")
        assertThat(apk).doesNotContainClass("Landroidx/preference/Preference;")

        // 2. Check that the library to refactor is not yet refactored
        assertThat(apk).hasClass("Lcom/example/androidlib/MyPreference;")
            .that().hasSuperclass("Landroid/support/v7/preference/Preference;")
    }

    @Test
    fun testJetifierEnabledAndroidXEnabled() {
        // Prepare the project to use AndroidX
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "compileSdkVersion rootProject.latestCompileSdk",
            "compileSdkVersion \"android-P\"")
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/main/java/com/example/app/MainActivity.java"),
            "import android.support.v7.app.AppCompatActivity;",
            "import androidx.appcompat.app.AppCompatActivity;")

        // Build the project with Jetifier enabled and AndroidX enabled
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .with(BooleanOption.ENABLE_JETIFIER, true)
            .run("assembleDebug")
        val apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG)

        // 1. Check that the old support library has been replaced with a new one
        assertThat(apk).doesNotContainClass("Landroid/support/v7/preference/Preference;")
        assertThat(apk).containsClass("Landroidx/preference/Preference;")

        // 2. Check that the library to refactor has been refactored
        assertThat(apk).hasClass("Lcom/example/androidlib/MyPreference;")
            .that().hasSuperclass("Landroidx/preference/Preference;")
    }

    @Test
    fun testJetifierEnabledAndroidXDisabled() {
        // Build the project with Jetifier enabled but AndroidX disabled, expect failure
        try {
            project.executor()
                .with(BooleanOption.USE_ANDROID_X, false)
                .with(BooleanOption.ENABLE_JETIFIER, true)
                .run("assembleDebug")
            fail("Expected BuildException")
        } catch (e: BuildException) {
            assertThat(Throwables.getStackTraceAsString(e))
                .contains("AndroidX must be enabled when Jetifier is enabled.")
        }
    }
}
