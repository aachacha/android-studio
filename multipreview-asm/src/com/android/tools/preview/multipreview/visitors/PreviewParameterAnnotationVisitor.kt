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

package com.android.tools.preview.multipreview.visitors

import com.android.tools.preview.multipreview.ParameterRepresentation
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes

/**
 * [AnnotationVisitor] that records the parameters of the annotations and adds them as a
 * [ParameterRepresentation] to the list of [parameters].
 */
internal class PreviewParameterAnnotationVisitor(
    private val parameters: MutableList<ParameterRepresentation>
) : AnnotationVisitor(Opcodes.ASM8) {
    private val annotationParams = mutableMapOf<String, Any?>()

    override fun visit(name: String?, value: Any?) {
        if (name != null) {
            annotationParams[name] = value
        }
    }

    override fun visitEnd() {
        parameters.add(ParameterRepresentation(annotationParams))
    }
}