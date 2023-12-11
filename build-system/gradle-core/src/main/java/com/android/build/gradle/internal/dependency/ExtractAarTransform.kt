/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.SdkConstants
import com.android.build.gradle.internal.caching.DisabledCachingReason
import com.android.builder.utils.isValidZipEntryName
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.io.Files
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.jar.JarOutputStream
import java.util.zip.ZipInputStream

/**
 * Transform that extracts an AAR file into a directory.
 *
 * Note: There are small adjustments made to the extracted contents (see [AarExtractor.extract]).
 */
@DisableCachingByDefault(because = DisabledCachingReason.COPY_TASK)
abstract class ExtractAarTransform: TransformAction<GenericTransformParameters> {

    @get:Classpath
    @get:InputArtifact
    abstract val aarFile: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val inputFile = aarFile.get().asFile
        val outputDir = outputs.dir(inputFile.nameWithoutExtension)
        FileUtils.mkdirs(outputDir)
        AarExtractor().extract(inputFile, outputDir)
    }
}

private const val LIBS_PREFIX = SdkConstants.LIBS_FOLDER + '/'
private const val LIBS_PREFIX_LENGTH = LIBS_PREFIX.length
private const val JARS_PREFIX_LENGTH = SdkConstants.FD_JARS.length + 1

@VisibleForTesting
internal class AarExtractor {

    /**
     * [StringBuilder] used to construct all paths. It gets truncated back to [JARS_PREFIX_LENGTH]
     * on every calculation.
     */
    private val stringBuilder = StringBuilder(60).apply {
        append(SdkConstants.FD_JARS)
        append(File.separatorChar)
    }

    private fun choosePathInOutput(entryName: String): String {
        stringBuilder.setLength(JARS_PREFIX_LENGTH)

        return when {
            entryName == SdkConstants.FN_CLASSES_JAR || entryName == SdkConstants.FN_LINT_JAR -> {
                stringBuilder.append(entryName)
                stringBuilder.toString()
            }
            entryName.startsWith(LIBS_PREFIX) -> {
                // In case we have libs/classes.jar we are going to rename them, due an issue in
                // Gradle.
                // TODO: stop doing this once this is fixed in gradle. b/65298222
                when (val pathWithinLibs = entryName.substring(LIBS_PREFIX_LENGTH)) {
                    SdkConstants.FN_CLASSES_JAR -> stringBuilder.append(LIBS_PREFIX).append("classes-2${SdkConstants.DOT_JAR}")
                    SdkConstants.FN_LINT_JAR -> stringBuilder.append(LIBS_PREFIX).append("lint-2${SdkConstants.DOT_JAR}")
                    else -> stringBuilder.append(LIBS_PREFIX).append(pathWithinLibs)
                }
                stringBuilder.toString()
            }
            else -> entryName
        }
    }

    /**
     * Extracts an AAR file into a directory.
     *
     * Note: There are small adjustments made to the extracted contents. For example, classes.jar
     * inside the AAR will be extracted to jars/classes.jar, and if the jar does not exist, we will
     * create an empty classes.jar.
     */
    fun extract(aar: File, outputDir: File) {
        ZipInputStream(aar.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.isDirectory || !isValidZipEntryName(entry) || entry.name.isEmpty()) {
                    continue
                }
                val path = FileUtils.toSystemDependentPath(choosePathInOutput(entry.name))
                val outputFile = File(outputDir, path)
                Files.createParentDirs(outputFile)
                Files.asByteSink(outputFile).writeFrom(zipInputStream)
            }
        }

        // If classes.jar does not exist, create an empty one
        val classesJar = outputDir.resolve("${SdkConstants.FD_JARS}/${SdkConstants.FN_CLASSES_JAR}")
        if (!classesJar.exists()) {
            // It's not required to create a manifest inside the empty jar, but if we ever want to
            // create it, be sure to set a fixed timestamp so that the jar is deterministic
            // (see b/315336689).
            FileUtils.mkdirs(classesJar.parentFile)
            JarOutputStream(classesJar.outputStream()).use {  }
        }
    }

}
