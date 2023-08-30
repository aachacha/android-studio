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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import kotlinx.coroutines.CoroutineScope
import java.net.Socket

class UnRootCommandHandler : DeviceCommandHandler("unroot") {

    override fun invoke(
        server: FakeAdbServer,
        socketScope: CoroutineScope,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        val outputStream = socket.getOutputStream()
        if (!device.isRoot) {
            writeOkay(outputStream)
            // Note: Response is not a length prefixed string, unlike most OKAY responses
            writeString(outputStream, ADBD_NOT_ROOT)
        } else {
            writeOkay(outputStream)
            // Note: Response is not a length prefixed string, unlike most OKAY responses
            writeString(outputStream, ADBD_RESTARTING_UNROOT)
            RootCommandHandler.restartDeviceAsRoot(server, device, isRoot = false)
        }
    }

    companion object {

        // The strings below come from the ADB Daemon code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/daemon/restart_service.cpp;l=46

        private const val ADBD_NOT_ROOT = "adbd not running as root\n"
        private const val ADBD_RESTARTING_UNROOT = "restarting adbd as non root\n"
    }
}
