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

package com.android.builder.internal.aapt.v2

import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.ide.common.res2.CompileResourceRequest
import com.android.utils.ILogger
import java.io.File
import java.util.concurrent.TimeoutException
import javax.annotation.concurrent.NotThreadSafe

/**
 * Manages an AAPT2 daemon process. Implementations are not expected to be thread safe.
 *
 * This must be used in the following sequence:
 * Call [compile] or [link] as many times as needed.
 * These methods block until the operation requested is complete.
 * The first call to either of [compile] or [link] will start the underlying daemon process.
 * Call [shutDown()], which blocks until the daemon process has exited.
 *
 * Processes cannot be re-started.
 *
 * The state tracking in this class is separated from the actual process handling to allow test
 * fakes that do not actually use AAPT.
 */
@NotThreadSafe
abstract class Aapt2Daemon(
        protected val displayName: String,
        protected val logger: ILogger) : Aapt2 {
    enum class State { NEW, RUNNING, SHUTDOWN }

    var state: State = State.NEW
        private set

    private fun checkStarted() {
        when (state) {
            State.NEW -> {
                logger.info("%1\$s: starting", displayName)
                state = State.RUNNING
                try {
                    startProcess()
                } catch (e: TimeoutException) {
                    handleTimeout("Daemon startup", e)
                }
            }
            State.RUNNING -> {
                // Already ready
            }
            State.SHUTDOWN -> throw IllegalStateException("$displayName: Cannot restart a shutdown process")
        }
    }

    /**
     * Implementors must start the underlying AAPT2 daemon process.
     *
     * This will be called before any calls to [doCompile] or [doLink].
     */
    @Throws(TimeoutException::class)
    protected abstract fun startProcess()

    override fun compile(request: CompileResourceRequest, logger: ILogger) {
        checkStarted()
        try {
            doCompile(request, logger)
        } catch (e: TimeoutException) {
            handleTimeout("Compile '${request.inputFile}'", e)
        }
    }

    /**
     * Implementors must compile the file in the request given.
     *
     * This will only be called after [startProcess] is called and before [stopProcess] is called
     */
    @Throws(TimeoutException::class)
    protected abstract fun doCompile(request: CompileResourceRequest, logger: ILogger)

    override fun link(request: AaptPackageConfig, tempDirectory: File) {
        checkStarted()
        try {
            doLink(request, tempDirectory)
        } catch (e: TimeoutException) {
            handleTimeout("Link", e)
        }
    }

    /**
     * Implementors must perform the link operation given.
     *
     * This will only be called after [startProcess] is called and before [stopProcess] is called.
     */
    @Throws(TimeoutException::class)
    protected abstract fun doLink(request: AaptPackageConfig, tempDirectory: File)

    fun shutDown() {
        state = when (state) {
            State.NEW -> State.SHUTDOWN // Never started, nothing to do.
            State.RUNNING -> {
                logger.info("%1\$s: shutdown", displayName)
                try {
                    stopProcess()
                } catch (e: TimeoutException) {
                    logger.error(e, "$displayName Failed to shutdown within timeout")
                }
                State.SHUTDOWN
            }
            State.SHUTDOWN -> throw IllegalStateException("Cannot call shutdown multiple times")
        }
    }

    /**
     * Implementors must stop the underlying AAPT2 daemon process.
     *
     * Will only be called if the process was started by [startProcess].
     */
    @Throws(TimeoutException::class)
    protected abstract fun stopProcess()

    /**
     * Timeouts are not generally expected.
     *
     * In the case of a timeout, shut down the whole process, as something has gone badly wrong.
     *
     * They could occur on loaded machines or be caused by anti-virus software.
     */
    private fun handleTimeout(action: String, exception: TimeoutException) {
        try {
            throw Aapt2InternalException(
                    "$displayName: $action timed out, attempting to stop daemon.\n" +
                            "This should not happen under normal circumstances, " +
                            "please file an issue if it does.",
                    exception)
        } finally {
            shutDown()
        }
    }
}