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

package com.android.tools.render

import com.android.sdklib.AndroidVersion
import com.android.tools.module.AndroidModuleInfo
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** [AndroidModuleInfo] wrapping a single [AndroidVersion]. */
internal class StandaloneModuleInfo(
    override val packageName: String,
    androidVersion: AndroidVersion
) : AndroidModuleInfo {
    override val runtimeMinSdkVersion: ListenableFuture<AndroidVersion>
        get() = Futures.immediateFuture(minSdkVersion)
    override val minSdkVersion: AndroidVersion = androidVersion
    override val targetSdkVersion: AndroidVersion = androidVersion
    override val buildSdkVersion: AndroidVersion = androidVersion
}