/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal

/**
 * Enums representing Maven coordinates that have usages
 */
enum class MavenCoordinates (val group: String, val artifact: String, val version: String) {
    ANDROIDX_PRIVACY_SANDBOX_SDK_API_GENERATOR_1_0_0_ALPHA03(
        "androidx.privacysandbox.tools",
        "tools-apigenerator",
        "1.0.0-alpha03"
    ),
    ANDROIDX_PRIVACYSANDBOX_TOOLS_TOOLS_API_PACKAGER_1_0_0_ALPHA_03(
        "androidx.privacysandbox.tools",
        "tools-apipackager",
        "1.0.0-alpha03"
    ),
    ORG_JETBRAINS_KOTLINX_KOTLINX_COROUTINES_ANDROID_1_6_4(
        "org.jetbrains.kotlinx",
        "kotlinx-coroutines-android",
        "1.6.4"
    ),
    KOTLIN_COMPILER_1_7_10(
        "org.jetbrains.kotlin",
        "kotlin-compiler-embeddable",
        "1.7.10");

    override fun toString(): String = "$group:$artifact:$version"
}
