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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.PackagingOptions
import com.android.build.gradle.internal.services.VariantServices

class ResourcesApkPackagingImpl(
    dslPackagingOptions: PackagingOptions,
    variantServices: VariantServices
) : ResourcesPackagingImpl(dslPackagingOptions, variantServices) {

    override val excludes =
        variantServices.setPropertyOf(String::class.java) {
            // exclude .kotlin_module files from APKs (b/152898926)
            getBaseExcludes().plus("/META-INF/*.kotlin_module")
        }
}


