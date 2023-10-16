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

import com.android.tools.rendering.ModuleRenderContext
import com.android.tools.rendering.classloading.ClassTransform
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderDiagnosticsRead
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.classloading.loaders.ClassLoaderLoader
import com.intellij.openapi.module.Module
import com.intellij.util.lang.UrlClassLoader
import kotlin.io.path.Path

/**
 * [ModuleClassLoaderManager] for the [ModuleClassLoader]s used in the standalone rendering.
 * Currently, it is a thin wrapper around [UrlClassLoader].
 * TODO(): Apply the bytecode processing similar to the one used in Studio.
 */
internal class StandaloneModuleClassLoaderManager(
    private val classPath: List<String>,
) : ModuleClassLoaderManager<ModuleClassLoader> {
    private class DefaultModuleClassLoader(
        parent: ClassLoader?,
        loader: Loader,
    ) : ModuleClassLoader(
        parent,
        loader
    ) {
        override val stats: ModuleClassLoaderDiagnosticsRead =
            object : ModuleClassLoaderDiagnosticsRead {
                override val classesFound: Long = 0
                override val accumulatedFindTimeMs: Long = 0
                override val accumulatedRewriteTimeMs: Long = 0
            }
        override val isUserCodeUpToDate: Boolean = true
        override fun hasLoadedClass(fqcn: String): Boolean = true
        override val isDisposed: Boolean = false
    }

    private fun createClassLoader(parent: ClassLoader?): DefaultModuleClassLoader =
        DefaultModuleClassLoader(
            parent,
            ClassLoaderLoader(
                UrlClassLoader.build()
                    .useCache(false)
                    .parent(parent)
                    .files(classPath.map { Path(it) })
                    .get()
            ),
        )

    override fun getShared(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        additionalProjectTransformation: ClassTransform,
        additionalNonProjectTransformation: ClassTransform,
        onNewModuleClassLoader: Runnable
    ): ModuleClassLoaderManager.Reference<ModuleClassLoader> {
        return ModuleClassLoaderManager.Reference(this, createClassLoader(parent))
    }

    override fun getPrivate(
        parent: ClassLoader?,
        moduleRenderContext: ModuleRenderContext,
        additionalProjectTransformation: ClassTransform,
        additionalNonProjectTransformation: ClassTransform
    ): ModuleClassLoaderManager.Reference<ModuleClassLoader> {
        return ModuleClassLoaderManager.Reference(this, createClassLoader(parent))
    }

    override fun release(moduleClassLoaderReference: ModuleClassLoaderManager.Reference<*>) { }

    override fun clearCache(module: Module) { }
}