/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.prefs

import java.io.File

/**
 * An implementation of AndroidLocationProvider to be used in tests.
 *
 * This is accessible through common's text fixtures artifact.
 */
class FakeAndroidLocationsProvider(override val userHomeLocation: File): AndroidLocationsProvider {

    override val prefsLocation: File
        get() = File(userHomeLocation, ".android").also { it.mkdirs() }

    override val avdLocation: File
        get() = File(prefsLocation, AbstractAndroidLocations.FOLDER_AVD)
}
