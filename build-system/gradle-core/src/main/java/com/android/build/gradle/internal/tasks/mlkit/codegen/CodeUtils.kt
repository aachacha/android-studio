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

@file:JvmName("CodeUtils")

package com.android.build.gradle.internal.tasks.mlkit.codegen

import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName

fun getParameterType(tensorInfo: TensorInfo): TypeName {
    return if (tensorInfo.source == TensorInfo.Source.INPUT) {
        if (tensorInfo.contentType == TensorInfo.ContentType.IMAGE) {
            ClassNames.TENSOR_IMAGE
        } else {
            ClassNames.TENSOR_BUFFER
        }
    } else {
        ClassNames.TENSOR_BUFFER
    }
}

fun getFileName(name: String): String {
    return name.replace("\\..*".toRegex(), "") + "Data"
}

fun getProcessorName(tensorInfo: TensorInfo): String {
    return if (tensorInfo.source == TensorInfo.Source.INPUT) {
        tensorInfo.name + "Processor"
    } else {
        tensorInfo.name + "PostProcessor"
    }
}

fun getProcessedTypeName(tensorInfo: TensorInfo): String {
    return "processed" + tensorInfo.name
}

fun getProcessorBuilderName(tensorInfo: TensorInfo): String {
    return getProcessorName(tensorInfo) + "Builder"
}

fun getFloatArrayString(array: FloatArray): String {
    return getArrayString("float", array.map { it.toString() + "f" }.toTypedArray())
}

fun getObjectArrayString(array: Array<String>): String {
    return getArrayString("Object", array)
}

private fun getArrayString(
    type: String,
    array: Array<String>
): String {
    val builder = StringBuilder()
    builder.append(String.format("new %s[] {", type))
    for (dim in array) {
        builder.append(dim).append(",")
    }
    builder.deleteCharAt(builder.length - 1)
    builder.append("}")
    return builder.toString()
}

fun getDataType(type: TensorInfo.DataType): String {
    return type.toString()
}

fun getOutputParameterType(tensorInfo: TensorInfo): ClassName {
    return if (tensorInfo.fileType == TensorInfo.FileType.TENSOR_AXIS_LABELS) {
        ClassNames.TENSOR_LABEL
    } else {
        ClassNames.TENSOR_BUFFER
    }
}