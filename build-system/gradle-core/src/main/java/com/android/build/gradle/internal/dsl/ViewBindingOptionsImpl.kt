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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.BuildFeatures
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import javax.inject.Inject

/** DSL object for configuring view binding options.  */
open class ViewBindingOptionsImpl @Inject constructor(
    private val features: BuildFeatures,
    private val projectOptions: ProjectOptions,
    private val dslScope: DslScope) : ViewBindingOptions {

    /** Whether to enable data binding.  */
    override var isEnabled: Boolean
        get() {
            dslScope.deprecationReporter
                .reportDeprecatedUsage(
                    "android.buildFeatures.viewBinding",
                    "android.viewBinding.enabled",
                    DeprecationReporter.DeprecationTarget.VERSION_5_0
                )
            val bool = features.viewBinding
            if (bool != null) {
                return bool
            }
            return projectOptions[BooleanOption.BUILD_FEATURE_VIEWBINDING]
        }
        set(value) {
            dslScope.deprecationReporter
                .reportDeprecatedUsage(
                    "android.buildFeatures.viewBinding",
                    "android.viewBinding.enabled",
                    DeprecationReporter.DeprecationTarget.VERSION_5_0
                )
            features.viewBinding = value
        }
}
