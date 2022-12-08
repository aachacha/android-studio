/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbDeviceServices
import com.android.adblib.ConnectedDevice
import com.android.adblib.ProcessIdList
import com.android.adblib.emptyProcessIdList
import com.android.adblib.scope
import com.android.adblib.selector
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessTracker
import com.android.adblib.tools.debugging.ProcessMap
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.EOFException
import java.time.Duration

internal class JdwpProcessTrackerImpl(
  override val device: ConnectedDevice
): JdwpProcessTracker {

    private val session
        get() = device.session

    private val logger = thisLogger(session)

    private val processesMutableFlow = MutableStateFlow<List<JdwpProcess>>(emptyList())

    override val scope = device.scope.createChildScope(isSupervisor = true)

    override val processesFlow = processesMutableFlow.asStateFlow()

    init {
        scope.launch {
            trackProcesses()
        }
    }

    private suspend fun trackProcesses() {
        val processMap = ProcessMap<JdwpProcessImpl>()
        var deviceDisconnected = false
        try {
            session.deviceServices
                .trackJdwp(device.selector)
                .retryWhen { throwable, _ ->
                    // We want to retry the `trackJdwp` request as long as the device is connected.
                    // But we also want to end the flow when the device has been disconnected.
                    if (!scope.isActive) {
                        logger.info { "JDWP tracker service ending because device is disconnected" }
                        deviceDisconnected = true
                        false // Don't retry, let exception through
                    } else {
                        // Retry after emitting empty list
                        if (throwable is EOFException) {
                            logger.info { "JDWP tracker services ended with expected EOF exception, retrying" }
                        } else {
                            logger.info(throwable) { "JDWP tracker ended unexpectedly ($throwable), retrying" }
                        }
                        // When disconnected, assume we have no processes
                        emit(emptyProcessIdList())
                        delay(TRACK_JDWP_RETRY_DELAY.toMillis())
                        true // Retry
                    }
                }.collect { processIdList ->
                    logger.debug { "Received a new list of processes: $processIdList" }
                    updateProcessMap(processMap, processIdList)
                    processesMutableFlow.emit(processMap.values.toList())
                }
        } catch (t: Throwable) {
            t.rethrowCancellation()
            if (deviceDisconnected) {
                logger.debug(t) { "Ignoring exception $t because device has been disconnected" }
            } else {
                throw t
            }
        } finally {
            logger.debug { "Clearing process map" }
            processMap.clear()
            processesMutableFlow.value = emptyList()
        }
    }

    private fun updateProcessMap(map: ProcessMap<JdwpProcessImpl>, list: ProcessIdList) {
        map.update(list, valueFactory = { pid ->
            logger.debug { "Adding process $pid to process map" }
            JdwpProcessImpl(session, device, pid).also {
                it.startMonitoring()
            }
        })
    }

    companion object {

        /**
         * If the [AdbDeviceServices.trackJdwp] call fails with an error while the device is
         * still connected, we want to retry. This defines the [Duration] to wait before retrying.
         */
        private val TRACK_JDWP_RETRY_DELAY = Duration.ofSeconds(2)
    }
}
