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
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.utils.ILogger
import java.util.concurrent.TimeoutException

class CompileLinkTimeoutAapt2Daemon(name: String = "Test") :
        Aapt2Daemon("$name Compile/Link Timeout AAPT Daemon", NoErrorsOrWarningsLogger()) {
    override fun startProcess() {
    }

    override fun doCompile(request: CompileResourceRequest, logger: ILogger) {
        throw TimeoutException("Compile timed out")
    }

    override fun doLink(request: AaptPackageConfig) {
        throw TimeoutException("Link timed out")
    }

    override fun stopProcess() {
    }
}