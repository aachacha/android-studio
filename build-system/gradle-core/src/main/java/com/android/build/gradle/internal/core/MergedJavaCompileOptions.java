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

package com.android.build.gradle.internal.core;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AnnotationProcessorOptions;
import com.android.build.gradle.api.JavaCompileOptions;

/** Implementation of CoreJavaCompileOptions used to merge multiple configs together. */
public class MergedJavaCompileOptions implements JavaCompileOptions {

    private com.android.build.gradle.internal.dsl.AnnotationProcessorOptions
            annotationProcessorOptions =
                    new com.android.build.gradle.internal.dsl.AnnotationProcessorOptions();

    @NonNull
    @Override
    public AnnotationProcessorOptions getAnnotationProcessorOptions() {
        return annotationProcessorOptions;
    }

    public void reset() {
        annotationProcessorOptions.getClassNames().clear();
        annotationProcessorOptions.getArguments().clear();
        annotationProcessorOptions.getCompilerArgumentProviders().clear();
    }

    public void append(@NonNull JavaCompileOptions javaCompileOptions) {
        annotationProcessorOptions.classNames(
                javaCompileOptions.getAnnotationProcessorOptions().getClassNames());
        annotationProcessorOptions.arguments(
                javaCompileOptions.getAnnotationProcessorOptions().getArguments());
        annotationProcessorOptions.compilerArgumentProviders(
                javaCompileOptions.getAnnotationProcessorOptions().getCompilerArgumentProviders());
        if (javaCompileOptions.getAnnotationProcessorOptions().getIncludeCompileClasspath() != null) {
            annotationProcessorOptions.setIncludeCompileClasspath(
                    javaCompileOptions.getAnnotationProcessorOptions().getIncludeCompileClasspath());
        }
    }
}
