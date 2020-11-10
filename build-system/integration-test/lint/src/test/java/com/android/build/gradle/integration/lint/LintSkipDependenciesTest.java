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

import static com.android.testutils.truth.FileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Assemble tests for lintLibraryModel.
 *
 * <p>To run just this test: ./gradlew :base:build-system:integration-test:lint:test --tests
 * LintSkipDependenciesTest
 */
@RunWith(FilterableParameterized.class)
public class LintSkipDependenciesTest {
    @Parameterized.Parameters(name = "{0}")
    public static LintInvocationType[] getParams() {
        return LintInvocationType.values();
    }

    @Rule
    public final GradleTestProject project;

    public LintSkipDependenciesTest(LintInvocationType lintInvocationType) {
        this.project = lintInvocationType.testProjectBuilder()
                .fromTestProject("lintLibrarySkipDeps")
                .create();
    }

    @Test
    public void checkLintDependenciesSkipped() throws IOException, InterruptedException {
        // Run twice to catch issues with configuration caching
        project.executor().run(":app:cleanLintDebug", ":app:lintDebug");
        project.executor().run(":app:cleanLintDebug", ":app:lintDebug");
        File file = new File(project.getSubproject("app").getProjectDir(), "lint-results.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly("No issues found.");
    }
}
