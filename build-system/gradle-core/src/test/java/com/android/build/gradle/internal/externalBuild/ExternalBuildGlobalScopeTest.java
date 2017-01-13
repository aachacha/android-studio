/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.externalBuild;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.build.gradle.AndroidGradleOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.OptionalCompilationStep;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the {@link ExternalBuildGlobalScope} class
 */
public class ExternalBuildGlobalScopeTest {

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    @Mock
    Project project;

    ExternalBuildGlobalScope scope;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        // Set a custom build cache directory so that the plugin won't attempt to use the default
        // build cache directory under the ".android" directory, which will fail in a testing
        // environment
        File buildCacheDir = testDir.newFolder();
        when(project.hasProperty(AndroidGradleOptions.PROPERTY_BUILD_CACHE_DIR)).thenReturn(true);
        when(project.property(AndroidGradleOptions.PROPERTY_BUILD_CACHE_DIR))
                .thenReturn(buildCacheDir.getPath());
        Project rootProject = mock(Project.class);
        when(project.getRootProject()).thenReturn(rootProject);
        when(rootProject.file(any())).thenAnswer(
                invocation -> new File((String) invocation.getArguments()[0]));
    }

    @Test
    public void testBuildDir() {
        when(project.getBuildDir()).thenReturn(new File("/tmp/out/folder"));
        scope = new ExternalBuildGlobalScope(project);
        assertThat(scope.getBuildDir().getPath()).isEqualTo(
                "/tmp/out/folder".replace('/', File.separatorChar));
    }


    @Test
    public void testIsActive() {
        when(project.hasProperty(AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS)).thenReturn(true);
        when(project.property(AndroidProject.PROPERTY_OPTIONAL_COMPILATION_STEPS)).thenReturn("INSTANT_DEV");
        scope = new ExternalBuildGlobalScope(project);

        assertThat(scope.isActive(OptionalCompilationStep.INSTANT_DEV)).isTrue();
        assertThat(scope.isActive(OptionalCompilationStep.RESTART_ONLY)).isFalse();
        assertThat(scope.isActive(OptionalCompilationStep.FULL_APK)).isFalse();
    }
}
