/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.sdklib.internal.avd

import java.nio.file.Path

sealed class Skin {
  abstract val name: String
}

/**
 * A skin that consists only of screen dimensions. (Sometimes referred to as a "magic" skin within
 * the emulator.)
 */
data class GenericSkin(val x: Int, val y: Int) : Skin() {
  override val name = "${x}x${y}"
}

/**
 * A skin described by a set of files within the directory specified by [path]. Should contain a
 * "layout" file and image files referenced by the layout.
 *
 * If the path is relative, it is interpreted relative to the SDK directory.
 */
data class OnDiskSkin(val path: Path) : Skin() {
  override val name = path.fileName.toString()
}
