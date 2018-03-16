/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants.FN_INTERMEDIATE_FULL_JAR
import com.android.annotations.VisibleForTesting
import com.android.build.api.artifact.BuildableArtifact
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.utils.FileUtils
import com.google.common.collect.Sets
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Task to merge the res/classes intermediate jars from a library into a single one  */
@CacheableTask
open class ZipMergingTask : AndroidVariantTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var libraryInputFiles: BuildableArtifact
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var javaResInputFiles: BuildableArtifact
        private set

    @get:OutputFile
    private lateinit var outputFile: File
        private set

    @VisibleForTesting
    internal fun init(
            libraryInputFiles: BuildableArtifact,
            javaResInputFiles: BuildableArtifact,
            outputFile: File) {
        this.libraryInputFiles = libraryInputFiles
        this.javaResInputFiles = javaResInputFiles
        this.outputFile = outputFile
    }

    @TaskAction
    @Throws(IOException::class)
    fun merge() {
        val buffer = ByteArray(8192)
        FileUtils.cleanOutputDir(outputFile.parentFile)
        FileOutputStream(outputFile).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                ZipOutputStream(bos).use { zos ->

                    val entries = Sets.newHashSet<String>()

                    for (inputFile in libraryInputFiles.files.union(javaResInputFiles.files)) {
                        FileInputStream(inputFile).use { fis ->
                            ZipInputStream(fis).use { zis ->

                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (entry.isDirectory) {
                                        continue
                                    }

                                    val entryName = entry.name
                                    if (entries.contains(entryName)) {
                                        // non class files can be duplicated between res and classes jar
                                        // due to annotation processor or other compiler (kotlin) generating
                                        // resources
                                        continue
                                    } else {
                                        entries.add(entryName)
                                    }

                                    zos.putNextEntry(entry)

                                    // read the content of the entry from the input stream, and write it into
                                    // the archive.
                                    var count = zis.read(buffer)
                                    while (count != -1) {
                                        zos.write(buffer, 0, count)
                                        count = zis.read(buffer)
                                    }

                                    // close the entries for this file
                                    zos.closeEntry()
                                    zis.closeEntry()
                                    entry = zis.nextEntry
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    class ConfigAction(private val scope: VariantScope) : TaskConfigAction<ZipMergingTask> {

        override fun getName(): String = scope.getTaskName("createFullJar")
        override fun getType(): Class<ZipMergingTask> = ZipMergingTask::class.java

        override fun execute(task: ZipMergingTask) {
            val buildArtifacts = scope.artifacts
            val mainFullJar = buildArtifacts.appendArtifact(InternalArtifactType.FULL_JAR,
                    task, FN_INTERMEDIATE_FULL_JAR)
            task.init(
                    buildArtifacts.getOptionalFinalArtifactFiles(InternalArtifactType.LIBRARY_CLASSES),
                    buildArtifacts.getOptionalFinalArtifactFiles(InternalArtifactType.LIBRARY_JAVA_RES),
                    mainFullJar)
            task.variantName = scope.fullVariantName
        }
    }
}
