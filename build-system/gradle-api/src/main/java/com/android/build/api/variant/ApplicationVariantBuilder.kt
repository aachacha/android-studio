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

package com.android.build.api.variant

/**
 * Model for application components that only contains configuration-time properties that impacts
 * the build flow.
 *
 * See [ComponentBuilder] and [VariantBuilder] for more information.
 */
interface ApplicationVariantBuilder : VariantBuilder,
    HasAndroidTestBuilder,
    HasUnitTestBuilder,
    HasTestFixturesBuilder,
    GeneratesApkBuilder,
    CanMinifyCodeBuilder,
    CanMinifyAndroidResourcesBuilder {

    /**
     * Whether the variant is debuggable.
     */
    val debuggable: Boolean

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    val dependenciesInfo: DependenciesInfoBuilder
}
