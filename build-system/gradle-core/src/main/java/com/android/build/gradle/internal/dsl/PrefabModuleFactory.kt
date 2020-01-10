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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.NamedDomainObjectFactory

/** Factory to create BuildType object using an [ObjectFactory] to add the DSL methods.  */
class PrefabModuleFactory(private val dslServices: DslServices) :
    NamedDomainObjectFactory<PrefabPackagingOptions> {

    override fun create(name: String): PrefabPackagingOptions {
        return dslServices.newInstance(PrefabPackagingOptions::class.java, name)
    }
}