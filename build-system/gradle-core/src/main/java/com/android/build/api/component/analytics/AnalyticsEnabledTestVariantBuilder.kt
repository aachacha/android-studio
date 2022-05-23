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

package com.android.build.api.component.analytics

import com.android.build.api.variant.TestVariantBuilder
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import javax.inject.Inject

/**
 * Shim object for [AnalyticsEnabledVariantBuilder] that records all mutating accesses to the analytics.
 */
open class AnalyticsEnabledTestVariantBuilder @Inject constructor(
        override val delegate: TestVariantBuilder,
        stats: GradleBuildVariant.Builder
) : AnalyticsEnabledVariantBuilder(delegate, stats),
    TestVariantBuilder{
    override var codeMinification: Boolean
        get() = delegate.codeMinification
        set(value) {
            stats.variantApiAccessBuilder.addVariantAccessBuilder().type = VariantMethodType.CODE_MINIFICATION_VALUE_VALUE
            delegate.codeMinification = value
        }
    }
