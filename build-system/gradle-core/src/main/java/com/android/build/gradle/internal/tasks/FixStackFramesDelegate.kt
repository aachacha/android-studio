/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.build.gradle.internal.tasks

import com.android.SdkConstants
import com.android.SdkConstants.DOT_JAR
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.builder.utils.isValidZipEntryName
import com.android.ide.common.resources.FileStatus
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import com.google.common.io.Closer
import java.io.File
import java.io.InputStream
import java.io.Serializable
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.attribute.FileTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.provider.Property
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

/**
 * When running Desugar, we need to make sure stack frames information is valid in the class files.
 * This is due to fact that Desugar may load classes in the JVM, and if stack frame information is
 * invalid for bytecode 1.7 and above, [VerifyError] is thrown. Also, if stack frames are
 * broken, ASM might be unable to read those classes.
 *
 * This delegate will load all class files from all external jars, and will use ASM to
 * recalculate the stack frames information. In order to obtain new stack frames, types need to be
 * resolved.
 *
 * The parent task requires external libraries as inputs, and all other scope types are
 * referenced. Reason is that loading a class from an external jar, might depend on loading a class
 * that could be located in any of the referenced scopes. In case we are unable to resolve types,
 * content of the original class file will be copied to the the output as we do not know upfront if
 * Desugar will actually load that type.
 */
class FixStackFramesDelegate(
    val bootClasspath: Set<File>,
    val classesToFix: Set<File>,
    val referencedClasses: Set<File>,
    val outFolder: File
) {

    /** ASM class writer that uses specified class loader to resolve types. */
    private class FixFramesVisitor(flags: Int, val classLoader: URLClassLoader) :
        ClassWriter(flags) {

        override fun getCommonSuperClass(type1: String, type2: String): String {
            var c: Class<*>
            val d: Class<*>
            try {
                c = Class.forName(type1.replace('/', '.'), false, classLoader)
                d = Class.forName(type2.replace('/', '.'), false, classLoader)
            } catch (e: Exception) {
                throw  RuntimeException(
                    "Unable to find common supper type for $type1 and $type2.",
                    e
                )
            }
            if (c.isAssignableFrom(d)) {
                return type1
            }
            if (d.isAssignableFrom(c)) {
                return type2
            }
            return if (c.isInterface || d.isInterface) {
                "java/lang/Object"
            } else {
                do {
                    c = c.superclass
                } while (!c.isAssignableFrom(d))
                c.name.replace('.', '/')
            }
        }
    }

    companion object {
        private val logger = LoggerWrapper.getLogger(FixStackFramesDelegate::class.java)
        private val zeroFileTime: FileTime = FileTime.fromMillis(0)

        // Shared state used in the worker actions.
        private val sharedState = WorkerActionServiceRegistry()
    }

    private fun createClassLoader(): URLClassLoader {
        val urls = ImmutableList.Builder<URL>()
        bootClasspath.forEach { file ->
            if (file.exists()) {
                urls.add(file.toURI().toURL())
            }
        }
        classesToFix.plus(referencedClasses).forEach { file ->
            if (file.isDirectory || file.isFile) {
                urls.add(file.toURI().toURL())
            }
        }
        return URLClassLoader(urls.build().toTypedArray())
    }

    private fun getUniqueName(input: File): String {
        return Hashing.sha256().hashString(input.absolutePath, StandardCharsets.UTF_8)
            .toString() + DOT_JAR
    }

    private fun processFiles(
        workers: WorkerExecutor,
        changedInput: Map<File, FileStatus>,
        task: AndroidVariantTask
    ) {
        Closer.create().use { closer ->
            val classLoader = createClassLoader()
            closer.register(classLoader)

            val classLoaderKey = ClassLoaderKey("classLoader" + hashCode())
            closer.register(sharedState.registerServiceAsCloseable(classLoaderKey, classLoader))

            changedInput.entries.forEach { entry ->
                val out = File(outFolder, getUniqueName(entry.key))

                Files.deleteIfExists(out.toPath())

                if (entry.value == FileStatus.NEW || entry.value == FileStatus.CHANGED) {
                    workers.noIsolation()
                        .submit(FixStackFramesRunnable::class.java) { params ->
                            params.initializeFromAndroidVariantTask(task)
                            params.input.set(entry.key)
                            params.output.set(out)
                            params.classLoaderKey.set(classLoaderKey)
                        }
                }
            }
            // We keep waiting for all the workers to finnish so that all the work is done before
            // we remove services in Manager.close()
            workers.await()
        }
    }

    fun doFullRun(workers: WorkerExecutor, task: AndroidVariantTask) {
        FileUtils.cleanOutputDir(outFolder)

        val inputToProcess = classesToFix.map { it to FileStatus.NEW }.toMap()

        processFiles(workers, inputToProcess, task)
    }

    fun doIncrementalRun(
        workers: WorkerExecutor,
        changedInput: Map<File, FileStatus>,
        task: AndroidVariantTask
    ) {
        // We should only process (unzip and fix stack) existing jar input from classesToFix
        // If changedInput contains a folder or deleted jar we will still try to delete
        // corresponding output entry (if exists) but will do no processing
        val jarsToProcess = classesToFix.filter(File::isFile).toSet()

        val inputToProcess = changedInput.entries.filter {
            it.value == FileStatus.REMOVED || jarsToProcess.contains(it.key)
        }.associate { it.key to it.value }

        processFiles(workers, inputToProcess, task)
    }

    open class BaseKey(val name: String) : Serializable {

        override fun equals(other: Any?): Boolean {
            if (other is BaseKey) {
                return this.name == other.name
            }
            return false
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    class ClassLoaderKey(name: String) : BaseKey(name),
        WorkerActionServiceRegistry.ServiceKey<URLClassLoader> {
        override val type: Class<URLClassLoader>
            get() = URLClassLoader::class.java
    }

    abstract class Params : ProfileAwareWorkAction.Parameters() {
        abstract val input: Property<File>

        abstract val output: Property<File>

        abstract val classLoaderKey: Property<ClassLoaderKey>
    }

    abstract class FixStackFramesRunnable : ProfileAwareWorkAction<Params>() {

        override fun run() {
            val classLoader = sharedState
                .getService(parameters.classLoaderKey.get())
                .service
            createFile(
                parameters.input.get(),
                parameters.output.get(),
                classLoader
            )
        }

        private fun createFile(input: File, output: File, classLoader: URLClassLoader) {
            ZipFile(input).use { inputZip ->
                ZipOutputStream(
                    Files.newOutputStream(output.toPath()).buffered()
                ).use { outputZip ->
                    val inEntries = inputZip.entries()
                    while (inEntries.hasMoreElements()) {
                        val entry = inEntries.nextElement()
                        if (!isValidZipEntryName(entry)) {
                            throw InvalidPathException(
                                entry.name,
                                "Entry name contains invalid characters"
                            )
                        }
                        if (!entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                            continue
                        }
                        val originalFile = inputZip.getInputStream(entry).buffered()
                        val outEntry = ZipEntry(entry.name)

                        val newEntryContent = getFixedClass(originalFile, classLoader)

                        val crc32 = CRC32()
                        crc32.update(newEntryContent)
                        outEntry.crc = crc32.value
                        outEntry.method = ZipEntry.STORED
                        outEntry.size = newEntryContent.size.toLong()
                        outEntry.compressedSize = newEntryContent.size.toLong()
                        outEntry.lastAccessTime = zeroFileTime
                        outEntry.lastModifiedTime = zeroFileTime
                        outEntry.creationTime = zeroFileTime

                        outputZip.putNextEntry(outEntry)
                        outputZip.write(newEntryContent)
                        outputZip.closeEntry()
                    }
                }
            }

        }

        private fun getFixedClass(
            originalFile: InputStream,
            classLoader: URLClassLoader
        ): ByteArray {
            val bytes = ByteStreams.toByteArray(originalFile)
            return try {
                val classReader = ClassReader(bytes)
                val classWriter = FixFramesVisitor(ClassWriter.COMPUTE_FRAMES, classLoader)
                classReader.accept(classWriter, ClassReader.SKIP_FRAMES)
                classWriter.toByteArray()
            } catch (t: Throwable) {
                // we could not fix it, just copy the original and log the exception
                logger.verbose(t.message!!)
                bytes
            }
        }
    }
}
