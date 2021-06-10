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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/** Features that apply to distribution by the bundle  */
interface Bundle {

    @get:Incubating
    val abi: BundleAbi

    @get:Incubating
    val density: BundleDensity

    @get:Incubating
    val language: BundleLanguage

    @get:Incubating
    val texture: BundleTexture

    @get:Incubating
    val deviceTier: BundleDeviceTier

    @Incubating
    fun abi(action: BundleAbi.() -> Unit)

    @Incubating
    fun density(action: BundleDensity.() -> Unit)

    @Incubating
    fun language(action: BundleLanguage.() -> Unit)

    @Incubating
    fun texture(action: BundleTexture.() -> Unit)

    @Incubating
    fun deviceTier(action: BundleDeviceTier.() -> Unit)
}
