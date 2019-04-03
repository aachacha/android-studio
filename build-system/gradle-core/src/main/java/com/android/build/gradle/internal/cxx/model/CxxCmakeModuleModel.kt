/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.model

import com.android.repository.Revision
import java.io.File

interface CxxCmakeModuleModel {
    /** Path to cmake.exe (ex /path/to/cmake/cmake.exe) */
    val cmakeExe: File
    /** The version of CMake found by executing CMake --version (ex 3.10.2) */
    val foundCmakeVersion: Revision
    /** Path to ninja.exe (ex /path/to/ninja/ninja.exe) */
    val ninjaExe: File
    /** Path to the CMake toolchain in NDK (ex /path/to/ndk/android.toolchain.cmake) */
    val cmakeToolchainFile: File
}