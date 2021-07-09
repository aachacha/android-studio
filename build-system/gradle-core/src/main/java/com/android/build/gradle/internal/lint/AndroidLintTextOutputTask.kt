/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.lint

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.internal.lint.AndroidLintWorkAction.Companion.ERRNO_CREATED_BASELINE
import com.android.build.gradle.internal.lint.AndroidLintWorkAction.Companion.ERRNO_ERRORS
import com.android.build.gradle.internal.lint.AndroidLintWorkAction.Companion.maybeThrowException
import com.android.build.gradle.internal.lint.LintTaskManager.Companion.isLintStderr
import com.android.build.gradle.internal.lint.LintTaskManager.Companion.isLintStdout
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.lang.RuntimeException

/**
 * Task to print lint text output to stdout or stderr if necessary.
 *
 * This task is never up-to-date because AGP should print lint's text output (if there are issues
 * and lintOptions is configured to print to stdout or stderr) even if the other lint tasks are
 * up-to-date.
 */
abstract class AndroidLintTextOutputTask : NonIncrementalTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val textReportInputFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val returnValueInputFile: RegularFileProperty

    @get:Input
    abstract val outputStream: Property<OutputStream>

    @get:Input
    abstract val fatalOnly: Property<Boolean>

    @get:Input
    abstract val android: Property<Boolean>

    @get:Input
    abstract val abortOnError: Property<Boolean>

    override fun doTaskAction() {
        if (outputStream.get() != OutputStream.NONE) {
            textReportInputFile.get().asFile.let { textReportFile ->
                if (!textReportFile.isFile) {
                    logger.debug("Missing lint intermediate text report.")
                    return@let
                }
                val text = textReportFile.readText()
                if (text.startsWith("No issues found")
                    || text.contains("0 errors, 0 warnings")) {
                        return@let
                }
                when (outputStream.get()) {
                    OutputStream.STDOUT -> logger.lifecycle(text)
                    OutputStream.STDERR -> logger.error(text)
                    else -> {}
                }
            }
        }
        returnValueInputFile.get().asFile.let { returnValueFile ->
            if (!returnValueFile.isFile) {
                throw RuntimeException("Missing lint invocation return value file.")
            }
            val returnValue = returnValueFile.readText().toInt()
            if (returnValue in HANDLED_ERRORS) {
                if (returnValue == ERRNO_ERRORS && !abortOnError.get()) {
                    return
                }
                maybeThrowException(returnValue, android.get(), fatalOnly.get())
            }
        }
    }

    class SingleVariantCreationAction(variant: VariantCreationConfig) :
            VariantCreationAction(variant) {
        override val name: String = computeTaskName("lint")
        override val fatalOnly = false
    }

    class LintVitalCreationAction(variant: VariantCreationConfig) :
        VariantCreationAction(variant) {
        override val name: String = computeTaskName("lintVital")
        override val fatalOnly = true

        override fun handleProvider(taskProvider: TaskProvider<AndroidLintTextOutputTask>) {
            creationConfig.taskContainer.assembleTask.dependsOn(taskProvider)
        }
    }

    abstract class VariantCreationAction(variant: VariantCreationConfig) :
        VariantTaskCreationAction<AndroidLintTextOutputTask, VariantCreationConfig>(variant) {

        override val type: Class<AndroidLintTextOutputTask>
            get() = AndroidLintTextOutputTask::class.java

        abstract val fatalOnly: Boolean

        override fun configure(task: AndroidLintTextOutputTask) {
            super.configure(task)
            task.group = JavaBasePlugin.VERIFICATION_GROUP
            task.description = "Print text output from the corresponding lint report task"
            task.android.setDisallowChanges(true)
            task.initializeCommonInputs(
                creationConfig.artifacts,
                creationConfig.globalScope.extension.lintOptions,
                fatalOnly
            )
        }
    }

    internal fun initializeCommonInputs(
        artifacts: ArtifactsImpl,
        lintOptions: LintOptions,
        fatalOnly: Boolean
    ) {
        textReportInputFile.setDisallowChanges(
            artifacts.get(
                when {
                    fatalOnly -> InternalArtifactType.LINT_VITAL_INTERMEDIATE_TEXT_REPORT
                    else -> InternalArtifactType.LINT_INTERMEDIATE_TEXT_REPORT
                }
            )
        )
        returnValueInputFile.setDisallowChanges(
            artifacts.get(
                when {
                    fatalOnly -> InternalArtifactType.LINT_VITAL_RETURN_VALUE
                    else -> InternalArtifactType.LINT_RETURN_VALUE
                }
            )
        )
        this.fatalOnly.setDisallowChanges(fatalOnly)
        abortOnError.setDisallowChanges(lintOptions.isAbortOnError)
        val textOutput = lintOptions.textOutput
        when {
            fatalOnly || (lintOptions.textReport && textOutput?.isLintStderr() == true) ->
                outputStream.setDisallowChanges(OutputStream.STDERR)
            // If text report is requested, but no path specified, use stdout, hence the ?: true
            lintOptions.textReport && textOutput?.isLintStdout() ?: true ->
                outputStream.setDisallowChanges(OutputStream.STDOUT)
            else -> outputStream.setDisallowChanges(OutputStream.NONE)
        }
    }

    internal fun configureForStandalone(
        artifacts: ArtifactsImpl,
        lintOptions: LintOptions,
        fatalOnly: Boolean = false
    ) {
        analyticsService.setDisallowChanges(getBuildService(project.gradle.sharedServices))
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Print text output from the corresponding lint report task"
        android.setDisallowChanges(false)
        variantName = ""
        initializeCommonInputs(artifacts, lintOptions, fatalOnly)
    }

    enum class OutputStream {
        STDOUT,
        STDERR,
        NONE
    }

    companion object {
        val HANDLED_ERRORS = listOf(ERRNO_ERRORS, ERRNO_CREATED_BASELINE)
    }
}
