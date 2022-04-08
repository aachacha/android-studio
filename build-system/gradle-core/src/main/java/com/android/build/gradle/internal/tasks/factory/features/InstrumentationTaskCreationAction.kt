/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory.features

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.features.InstrumentationCreationConfig

/**
 * Creation action for tasks that requires instrumentation support.
 *
 * Example:
 * ```
 * abstract class Task {
 *   class CreationAction(
 *     creationConfig: ComponentCreationConfig
 *   ) : VariantTaskCreationAction<Task, ComponentCreationConfig>(
 *     creationConfig
 *   ), InstrumentationTaskCreationAction by InstrumentationTaskCreationActionImpl(
 *     creationConfig
 *   ) {
 *     ...
 *   }
 * }
 * ```
 */
interface InstrumentationTaskCreationAction {
    val instrumentationCreationConfig: InstrumentationCreationConfig
}

class InstrumentationTaskCreationActionImpl(
    creationConfig: ComponentCreationConfig
): InstrumentationTaskCreationAction {

    override val instrumentationCreationConfig: InstrumentationCreationConfig =
        creationConfig.instrumentationCreationConfig!!
}
