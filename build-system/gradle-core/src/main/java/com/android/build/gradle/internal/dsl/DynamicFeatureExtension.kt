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

import com.android.build.api.dsl.DynamicFeatureBuildFeatures
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

internal open class DynamicFeatureExtension(
    project: Project,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: DynamicFeatureExtensionImpl
) : AppExtension(
    project, projectOptions, globalScope,
    buildOutputs, sourceSetManager, extraModelInfo, false
), DynamicFeatureExtension<
        BuildType,
        DefaultConfig,
        ProductFlavor,
        SigningConfig> by publicExtensionImpl,
    ActionableVariantObjectOperationsExecutor by publicExtensionImpl {

    override val dataBinding: DataBindingOptions =
        project.objects.newInstance(
            DataBindingOptions::class.java,
            publicExtensionImpl.buildFeatures,
            projectOptions,
            globalScope.dslScope
        )

    override val viewBinding: ViewBindingOptions =
        project.objects.newInstance(
            ViewBindingOptionsImpl::class.java,
            publicExtensionImpl.buildFeatures,
            projectOptions,
            globalScope.dslScope
        )

    // this is needed because the impl class needs this but the interface does not,
    // so CommonExtension does not define it, which means, that even though it's part of
    // DynamicFeatureExtensionImpl, the implementation by delegate does not bring it.
    fun buildFeatures(action: Action<DynamicFeatureBuildFeatures>) {
        publicExtensionImpl.buildFeatures(action)
    }
}
