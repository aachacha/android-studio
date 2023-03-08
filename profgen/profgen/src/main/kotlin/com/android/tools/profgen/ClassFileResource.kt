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

package com.android.tools.profgen

import java.io.Closeable
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val CLASS_EXTENSION = ".class"
private const val MODULE_INFO_CLASS = "module-info.class"

interface ClassFileResource {
    fun getByteStream(): InputStream

    fun getBytes(): ByteArray {
        getByteStream().use {
            return it.readBytes()
        }
    }
}

internal class ArchiveClassFileResourceProvider (
    private val archive: Path,
    private var zipFile: ZipFile? = null
): Closeable {

    fun getClassFileResources(): Collection<ClassFileResource> {
        val classFileResources = mutableListOf<ClassFileResource>()
        val zipFile = zipFile ?: ZipFile(archive.toFile(), StandardCharsets.UTF_8).also {
            zipFile = it
        }
        val zipEntries = zipFile.entries()
        while (zipEntries.hasMoreElements()) {
            val zipEntry = zipEntries.nextElement()
            if (isClassFile(zipEntry)) {
                val classFileResource = object : ClassFileResource {
                    override fun getByteStream(): InputStream {
                        return zipFile.getInputStream(zipEntry)
                    }
                }
                classFileResources.add(classFileResource)
            }
        }
        return classFileResources
    }

    override fun close() {
        zipFile?.close()
    }
}

private fun isClassFile(zipEntry: ZipEntry): Boolean {
    val name = zipEntry.getName().toLowerCase(Locale.getDefault())
    return name.endsWith(CLASS_EXTENSION)
            && !name.endsWith(MODULE_INFO_CLASS)
            && !name.startsWith("meta-inf")
            && !name.startsWith("/meta-inf")
}
