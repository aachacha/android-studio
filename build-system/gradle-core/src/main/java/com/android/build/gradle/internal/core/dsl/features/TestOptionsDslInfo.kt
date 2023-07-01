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

package com.android.build.gradle.internal.core.dsl.features

import com.android.build.api.dsl.EmulatorControl
import com.android.build.api.dsl.EmulatorSnapshots
import com.android.build.api.dsl.ManagedDevices
import org.gradle.api.tasks.testing.Test

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by components that configure and run unit tests
 * and instrumentation tests
 */
interface TestOptionsDslInfo {
    val isIncludeAndroidResources: Boolean
    val isReturnDefaultValues: Boolean
    val animationsDisabled: Boolean
    val execution: String
    fun applyConfiguration(task: Test)

    val resultsDir: String?
    val reportDir: String?

    val managedDevices: ManagedDevices
    val emulatorControl: EmulatorControl
    val emulatorSnapshots: EmulatorSnapshots
}
