/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.kmp

import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.external.extras
import org.jetbrains.kotlin.tooling.core.extrasKeyOf

/**
 * A marker for android sourceSets in order to handle IDE import configuration for these sourceSets.
 */
class KotlinAndroidSourceSetMarker {
    companion object {
        private val extrasKey = extrasKeyOf<KotlinAndroidSourceSetMarker>()

        @OptIn(ExternalKotlinTargetApi::class)
        var KotlinSourceSet.android: KotlinAndroidSourceSetMarker?
            get() = extras[extrasKey]
            set(value) {
                if (value != null) extras[extrasKey] = value
                else extras.remove(extrasKey)
            }
    }
}