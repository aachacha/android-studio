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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbLogger
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.ddmlibcompatibility.debugging.ProcessTrackerHost.ClientUpdateKind
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.trackDevices
import com.android.adblib.withPrefix
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.ProfileableClient
import com.android.ddmlib.clientmanager.DeviceClientManager
import com.android.ddmlib.clientmanager.DeviceClientManagerListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Maximum amount of time to wait for a device to show up in [AdbSession.trackDevices]
 * after an [AdbLibDeviceClientManager] instance is created.
 */
private val DEVICE_TRACKER_WAIT_TIMEOUT = Duration.ofSeconds(2)

internal class AdbLibDeviceClientManager(
    private val clientManager: AdbLibClientManager,
    private val bridge: AndroidDebugBridge,
    private val iDevice: IDevice,
    private val listener: DeviceClientManagerListener
) : DeviceClientManager {

    private val deviceSelector = DeviceSelector.fromSerialNumber(iDevice.serialNumber)

    private val logger = thisLogger(clientManager.session).withPrefix("device '$deviceSelector': ")

    internal val session: AdbSession
        get() = clientManager.session

    private val clientList = AtomicReference<List<Client>>(emptyList())

    private val profileableClientList = AtomicReference<List<ProfileableClient>>(emptyList())

    private val ddmlibEventQueue = DdmlibEventQueue(logger, "ProcessUpdates")

    override fun getDevice(): IDevice {
        return iDevice
    }

    override fun getClients(): MutableList<Client> {
        return clientList.get().toMutableList()
    }

    override fun getProfileableClients(): MutableList<ProfileableClient> {
        return profileableClientList.get().toMutableList()
    }

    fun startDeviceTracking() {
        session.scope.launch {
            ddmlibEventQueue.runDispatcher()
        }

        session.scope.launch {
            // Wait for the device to show in the list of tracked devices
            val connectedDevice = withTimeoutOrNull(DEVICE_TRACKER_WAIT_TIMEOUT.toMillis()) {
                waitForConnectedDevice(iDevice.serialNumber)
            } ?: run {
                val msg = "Could not find device ${iDevice.serialNumber} in list of tracked devices"
                logger.info { msg }
                throw CancellationException(msg)
            }

            // Track processes running on the device
            launchProcessTracking(connectedDevice)
        }
    }

    private suspend fun waitForConnectedDevice(serialNumber: String): ConnectedDevice {
        logger.debug { "Waiting for device '$serialNumber' to show up in device tracker" }
        return session.connectedDevicesTracker.connectedDevices
            .mapNotNull { connectedDevices ->
                connectedDevices.firstOrNull { device -> device.serialNumber == serialNumber }
            }.first().also {
                logger.debug { "Found device '$serialNumber' ($it) in device tracker" }
            }
    }

    private fun launchProcessTracking(device: ConnectedDevice) {
        val host = ProcessTrackerHostImpl(device)
        JdwpTracker(host).startTracking()
    }

    suspend fun postClientUpdateEvent(client: AdblibClientWrapper, updateKind: ClientUpdateKind): Deferred<Unit> {
        logger.verbose { "Posting client update event: ${client.clientData.pid}: $updateKind" }
        val processed = CompletableDeferred<Unit>(client.jdwpProcess.scope.coroutineContext.job)
        ddmlibEventQueue.post(client.jdwpProcess.scope, "client update: $updateKind") {
            when (updateKind) {
                ClientUpdateKind.HeapAllocations -> {
                    listener.processHeapAllocationsUpdated(bridge, this, client)
                }

                ClientUpdateKind.ProfilingStatus -> {
                    listener.processMethodProfilingStatusUpdated(bridge, this, client)
                }

                ClientUpdateKind.NameOrProperties -> {
                    // Note that "name" is really "any property"
                    listener.processNameUpdated(bridge, this, client)
                }

                ClientUpdateKind.DebuggerConnectionStatus -> {
                    listener.processDebuggerStatusUpdated(bridge, this, client)
                }
            }
            processed.complete(Unit)
        }
        return processed
    }

    class DdmlibEventQueue(logger: AdbLogger, name: String) {

        private val logger = logger.withPrefix("DDMLIB EventQueue '$name': ")

        /**
         * We limit to [QUEUE_CAPACITY] events in case a ddmlib handler is slowing down
         * event dispatching. When the limit is reached, [posting][post] events is throttled.
         */
        private val queue = Channel<Event>(QUEUE_CAPACITY)

        suspend fun post(scope: CoroutineScope, name: String, handler: () -> Unit) {
            queue.send(Event(scope, name, handler))
        }

        suspend fun runDispatcher() {
            queue.receiveAsFlow().collect { event ->
                event.scope.launch {
                    kotlin.runCatching {
                        logger.verbose { "Invoking ddmlib listener '${event.name}'" }
                        event.handler()
                        logger.verbose { "Invoking ddmlib listener '${event.name}' - done" }
                    }.onFailure { throwable ->
                        logger.warn(
                            throwable,
                            "Invoking ddmlib listener '${event.name}' threw an exception: $throwable"
                        )
                    }
                }.join()
            }
        }

        private class Event(val scope: CoroutineScope, val name: String, val handler: () -> Unit)

        companion object {

            const val QUEUE_CAPACITY = 1_000
        }
    }

    inner class ProcessTrackerHostImpl(override val device: ConnectedDevice) : ProcessTrackerHost {

        override val iDevice: IDevice
            get() = this@AdbLibDeviceClientManager.iDevice

        override suspend fun clientsUpdated(list: List<Client>) {
            logger.debug { "Updating list of clients: $list" }
            clientList.set(list.toList())
            ddmlibEventQueue.post(device.scope, "processListUpdated") {
                listener.processListUpdated(bridge, this@AdbLibDeviceClientManager)
            }
        }

        override suspend fun postClientUpdated(
            clientWrapper: AdblibClientWrapper,
            updateKind: ClientUpdateKind
        ): Deferred<Unit> {
            return postClientUpdateEvent(clientWrapper, updateKind)
        }
    }

}
