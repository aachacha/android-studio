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

import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.GenericVariantFilterBuilder
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantProperties
import com.android.build.api.variant.impl.GenericVariantFilterBuilderImpl
import com.android.build.gradle.internal.scope.VariantScope

/** Internal implementation of the 'new' DSL interface */
class TestExtensionImpl : CommonExtensionImpl<TestVariant, TestVariantProperties>(),
    TestExtension,
    ActionableVariantObjectOperationsExecutor {
    override fun executeVariantOperations(variantScopes: List<VariantScope>) {
        variantOperations.executeOperations<TestVariant>(variantScopes)
    }

    override fun executeVariantPropertiesOperations(variantScopes: List<VariantScope>) {
        variantPropertiesOperations.executeOperations<TestVariantProperties>(variantScopes)
    }

    override fun onVariants(): GenericVariantFilterBuilder<TestVariant> {
        return GenericVariantFilterBuilderImpl(
            variantOperations, TestVariant::class.java
        )
    }

    override fun onVariantProperties(): GenericVariantFilterBuilder<TestVariantProperties> {
        return GenericVariantFilterBuilderImpl(
            variantPropertiesOperations,
            TestVariantProperties::class.java
        )
    }
}