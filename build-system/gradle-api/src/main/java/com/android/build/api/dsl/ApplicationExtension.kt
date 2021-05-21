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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Extension for the Android Gradle Plugin Application plugin.
 *
 * This is the `android` block when the `com.android.application` plugin is applied.
 *
 * Only the Android Gradle Plugin should create instances of interfaces in com.android.build.api.dsl.
 */
interface ApplicationExtension :
    CommonExtension<
            ApplicationBuildFeatures,
            ApplicationBuildType,
            ApplicationDefaultConfig,
            ApplicationProductFlavor>,
    ApkExtension,
    TestedExtension {
    // TODO(b/140406102)

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    @get:Incubating
    val dependenciesInfo: DependenciesInfo

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    @Incubating
    fun dependenciesInfo(action: DependenciesInfo.() -> Unit)

    @get:Incubating
    val bundle: Bundle

    @Incubating
    fun bundle(action: Bundle.() -> Unit)

    @get:Incubating
    val dynamicFeatures: MutableSet<String>

    /**
     * Set of asset pack subprojects to be included in the app's bundle.
     */
    @get:Incubating
    val assetPacks: MutableSet<String>

    /**
     * Customizes publishing build variant artifacts from app module to a Maven repository.
     *
     * For more information about the properties you can configure in this block, see [ApplicationPublishing]
     */
    @get:Incubating
    val publishing: ApplicationPublishing

    /**
     * Customizes publishing build variant artifacts from app module to a Maven repository.
     *
     * For more information about the properties you can configure in this block, see [ApplicationPublishing]
     */
    @Incubating
    fun publishing(action: ApplicationPublishing.() -> Unit)
}
