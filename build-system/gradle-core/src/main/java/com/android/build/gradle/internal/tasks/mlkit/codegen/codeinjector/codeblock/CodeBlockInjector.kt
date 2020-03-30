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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock

import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.MethodSpec

/** Injects code block into method based on [TensorInfo]. */
abstract class CodeBlockInjector : CodeInjector<MethodSpec.Builder, TensorInfo> {
    abstract override fun inject(methodBuilder: MethodSpec.Builder, tensorInfo: TensorInfo)
}