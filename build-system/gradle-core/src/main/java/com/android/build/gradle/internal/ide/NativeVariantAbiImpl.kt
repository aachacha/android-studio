/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.ide

import com.android.builder.model.NativeArtifact
import com.android.builder.model.NativeSettings
import com.android.builder.model.NativeToolchain
import com.android.builder.model.NativeVariantAbi
import java.io.File

/**
 * A basic implementation of NativeVariantAbi.
 */
data class NativeVariantAbiImpl(
    private val buildFiles : Collection<File>,
    private val artifacts : Collection<NativeArtifact>,
    private val toolchains : Collection<NativeToolchain>,
    private val settings : Collection<NativeSettings>,
    private val fileExtensions : Map<String, String>) : NativeVariantAbi {
    override fun getBuildFiles() = buildFiles
    override fun getArtifacts() = artifacts
    override fun getToolChains() = toolchains
    override fun getSettings() = settings
    override fun getFileExtensions() = fileExtensions
}