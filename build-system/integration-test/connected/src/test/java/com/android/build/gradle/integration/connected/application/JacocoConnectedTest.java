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

package com.android.build.gradle.integration.connected.application;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.category.DeviceTests;
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.utils.FileUtils;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExternalResource;

public class JacocoConnectedTest {

    @ClassRule public static final ExternalResource emulator = EmulatorUtils.getEmulator();

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.WARN)
                    .addGradleProperties(
                            "org.gradle.unsafe.configuration-cache.max-problems=23") // b/158092419
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "\nandroid.buildTypes.debug.testCoverageEnabled true");
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor().run("connectedCheck");
        assertThat(project.file("build/reports/coverage/debug/index.html")).exists();
        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.html"))
                .exists();
    }

    @Test
    @Ignore("b/172657145")
    public void connectedCheckNamespacedRClasses() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.aaptOptions.namespaced = true\n");

        project.executor().run("connectedCheck");

        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.html"))
                .exists();
    }

    @Test
    public void connectedCheckWithOrchestrator() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion 16\n"
                        + "android.defaultConfig.testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'\n"
                        + "android.defaultConfig.testInstrumentationRunnerArguments package: 'com.example.helloworld'\n"
                        + "android.testOptions.execution 'ANDROIDX_TEST_ORCHESTRATOR'\n"
                        // Orchestrator requires some setup time and it usually takes
                        // about an minute. Increase the timeout for running "am instrument" command
                        // to 3 minutes.
                        + "android.adbOptions.timeOutInMs=180000\n"
                        + "dependencies {\n"
                        + "  androidTestImplementation 'androidx.test:core:1.3.1-alpha02'\n"
                        + "  androidTestImplementation 'androidx.test.ext:junit:1.1.3-alpha02'\n"
                        + "  androidTestImplementation 'androidx.test:monitor:1.3.1-alpha02'\n"
                        + "  androidTestImplementation 'androidx.test:runner:1.3.1-alpha02'\n"
                        + "  androidTestImplementation 'androidx.test.services:test-services:1.3.1-alpha02'\n"
                        + "  androidTestUtil 'androidx.test:orchestrator:1.3.1-alpha02'\n"
                        + "}");
        TestFileUtils.appendToFile(
                project.getGradlePropertiesFile(),
                "android.useAndroidX=true");

        String testSrc =
                "package com.example.helloworld;\n"
                        + "\n"
                        + "import androidx.test.ext.junit.runners.AndroidJUnit4;\n"
                        + "import org.junit.Test;\n"
                        + "import org.junit.runner.RunWith;\n"
                        + "\n"
                        + "@RunWith(AndroidJUnit4.class)\n"
                        + "public class ExampleTest {\n"
                        + "    @Test\n"
                        + "    public void test1() { }\n"
                        + "\n"
                        + "    @Test\n"
                        + "    public void test2() { }\n"
                        + "}\n";
        Path exampleTest =
                project.getProjectDir()
                        .toPath()
                        .resolve("src/androidTest/java/com/example/helloworld/ExampleTest.java");
        Files.createDirectories(exampleTest.getParent());
        Files.write(exampleTest, testSrc.getBytes());

        // This example project uses deprecated "android.support.test.runner.AndroidJUnit4" runner
        // which cannot be used with androidx.test.ext.junit.runners.AndroidJUnit4 together. So,
        // deleting it here.
        Path deprecatedTest =
                project.getProjectDir()
                        .toPath()
                        .resolve("src/androidTest/java/com/example/helloworld/HelloWorldTest.java");
        Files.deleteIfExists(deprecatedTest);

        project.executor().withArgument("--debug").run("connectedCheck");
        List<File> files =
                FileUtils.find(
                        project.file("build/outputs/code_coverage"), Pattern.compile(".*\\.ec"));

        // ExampleTest has 2 methods, and there should be at least 2 .ec files
        Truth.assertThat(files.size()).isAtLeast(2);
        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.html"))
                .exists();
    }

    /** Regression test for http://b/152872138. */
    @Test
    @Category(DeviceTests.class)
    public void testDisablingBuildFeatures() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n\n"
                        + "android {\n"
                        + "  buildFeatures {\n"
                        + "    aidl = false\n"
                        + "    renderScript = false\n"
                        + "  }\n"
                        + "}\n");
        project.executor().run("connectedCheck");
        assertThat(
                        project.file(
                                "build/reports/coverage/debug/com.example.helloworld/HelloWorld.html"))
                .exists();
    }
}
