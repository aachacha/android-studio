/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.lint;

import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.TaskStateList;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Checks if fatal lint errors stop the release build. */
@RunWith(FilterableParameterized.class)
public class LintVitalTest {

    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return LintInvocationType.values();
    }

    @Rule
    public final GradleTestProject project;

    private LintInvocationType lintInvocationType;

    public LintVitalTest(LintInvocationType lintInvocationType) {
        this.project = lintInvocationType.testProjectBuilder()
                .fromTestApp(HelloWorldApp.noBuildFile())
                .create();
        this.lintInvocationType = lintInvocationType;
    }

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    defaultConfig.minSdk = "
                        + TestVersions.SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "    dependenciesInfo.includeInApk = false"
                        + "}\n"
                        + "dependencies {\n"
                        + "    androidTestImplementation \"com.android.support.test:runner:${project.testSupportLibVersion}\"\n"
                        + "    androidTestImplementation \"com.android.support.test:rules:${project.testSupportLibVersion}\"\n"
                        + "}\n"
                        // Make sure lint task is created on plugin apply, not afterEvaluate.
                        + "task(\"myCheck\").dependsOn(lint)\n"
                        + "\n");

        File manifest = project.file("src/main/AndroidManifest.xml");
        TestFileUtils.searchAndReplace(
                manifest, "package=", "android:debuggable=\"true\"\npackage=");
    }

    /**
     * Because :lintVitalRelease is quite an expensive operation, there is logic in it that skips
     * its execution if :lint is present in the task graph (as :lintVitalRelease is a subset of what
     * :lint does, there's no point in doing both).
     */
    @Test
    public void runningLintSkipsLintVital() throws Exception {
        GradleBuildResult result = project.executor().expectFailure().run("lintVitalRelease", "lint");
        TruthHelper.assertThat(result.getTask(":lintVitalRelease")).wasSkipped();

        // We make this assertion to ensure that lint is actually run and runs as expected. Without
        // this, it's possible that we break the execution in some other way and the test still
        // passes.
        final String expectedFailedTaskPath;
        if (lintInvocationType == LintInvocationType.NEW_LINT_MODEL) {
            expectedFailedTaskPath = ":lintDebug";
        } else {
            expectedFailedTaskPath = ":lint";
        }

        TaskStateList.TaskInfo task = result.getTask(expectedFailedTaskPath);
        assertThat(task).failed();
    }

    @Test
    public void fatalLintCheckFailsBuild() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().expectFailure().run("assembleRelease");
        assertThat(result.getFailureMessage()).contains("fatal errors");
        TruthHelper.assertThat(result.getTask(":lintVitalRelease")).failed();
    }

    @Test
    public void lintVitalIsNotRunForLibraries() throws IOException, InterruptedException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "com.android.application", "com.android.library");
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "dependenciesInfo.includeInApk = false", "");
        GradleBuildResult result = project.executor().run("assembleRelease");
        assertThat(result.findTask(":lintVitalRelease")).isNull();
    }

    @Test
    public void lintVitalDisabled() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.lintOptions.checkReleaseBuilds = false\n");

        GradleBuildResult result = project.executor().run("assembleRelease");
        assertThat(result.findTask(":lintVitalRelease")).isNull();
    }
}
