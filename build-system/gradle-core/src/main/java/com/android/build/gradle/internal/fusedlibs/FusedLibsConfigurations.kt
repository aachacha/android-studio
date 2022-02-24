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

package com.android.build.gradle.internal.fusedlibs

import com.android.build.api.attributes.BuildTypeAttr
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage

/**
 * Scope object that contains all the configurations for the fused-libraries plugin.
 */
class FusedLibsConfigurations {

    private val configurations= mutableListOf<Configuration>()

    fun addConfiguration(configuration: Configuration) {
        synchronized(configurations) {
            configurations.add(configuration)
        }
    }

    fun getConfiguration(usage: String): Configuration {
        synchronized(configurations) {
            configurations.forEach { configuration ->
                if (configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name == usage)
                    return configuration
            }
        }
        throw IllegalArgumentException("No configuration found with usage $usage")
    }
}
