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
package com.android.declarative.internal.parsers

import com.android.declarative.internal.model.PluginInfo
import com.android.declarative.internal.toml.mapTable
import com.android.declarative.internal.toml.safeGetString
import org.tomlj.TomlArray

class PluginParser {
    fun parse(plugins: TomlArray): List<PluginInfo> =
        plugins.mapTable { plugin ->
            PluginInfo(plugin.safeGetString("id"))
        }
}