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

package com.android.build.gradle.internal.core.dsl

import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import com.android.build.gradle.internal.core.dsl.impl.SigningConfigResolver
import com.android.build.gradle.options.ProjectOptions

/**
 * Contains the final dsl info computed from the DSL object model (extension, default config,
 * build type, flavors) that are needed by components that produces APKs.
 */
interface ApkProducingComponentDslInfo: ConsumableComponentDslInfo {

    val dexingDslInfo: DexingDslInfo

    val isDebuggable: Boolean

    /**
     * Holds all SigningConfig information from the DSL and/or [ProjectOptions].
     * Will resolve config as soon as all changes been done and variant is created.
     */
    val signingConfigResolver: SigningConfigResolver?
}
