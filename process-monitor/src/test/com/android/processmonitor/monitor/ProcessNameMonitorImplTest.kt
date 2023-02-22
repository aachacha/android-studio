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
package com.android.processmonitor.monitor

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.IDevice
import com.android.processmonitor.common.DeviceEvent.DeviceDisconnected
import com.android.processmonitor.common.DeviceEvent.DeviceOnline
import com.android.processmonitor.common.DeviceTracker
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.monitor.ddmlib.FakeDeviceTracker
import com.android.processmonitor.monitor.ddmlib.mockDevice
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [ProcessNameMonitorImpl]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ProcessNameMonitorImplTest {

    private val fakeProcessTrackerFactory = object : FakeProcessTrackerFactory<IDevice>() {
        override fun getSerialNumber(device: IDevice) = device.serialNumber
    }

    @Test
    fun devicesOnline(): Unit = runTest {
        FakeDeviceTracker().use { deviceTracker ->
            val monitor =
                processNameMonitor(deviceTracker, trackerFactory = fakeProcessTrackerFactory)

            deviceTracker.sendDeviceEvents(DeviceOnline(mockDevice("device1")))
            deviceTracker.sendDeviceEvents(DeviceOnline(mockDevice("device2")))
            advanceUntilIdle()

            fakeProcessTrackerFactory.send("device1", ProcessAdded(1, "package1", "process1"))
            fakeProcessTrackerFactory.send("device2", ProcessAdded(2, "package2", "process2"))
            advanceUntilIdle()

            assertThat(monitor.getProcessNames("device1", 1))
                .isEqualTo(ProcessNames("package1", "process1"))
            assertThat(monitor.getProcessNames("device2", 2))
                .isEqualTo(ProcessNames("package2", "process2"))
        }
    }

    @Test
    fun deviceDisconnected(): Unit = runTest {
        FakeDeviceTracker().use { deviceTracker ->
            val monitor = processNameMonitor(deviceTracker)

            val device1 = mockDevice("device1")
            deviceTracker.sendDeviceEvents(DeviceOnline(device1))
            deviceTracker.sendDeviceEvents(DeviceOnline(mockDevice("device2")))
            advanceUntilIdle()

            fakeProcessTrackerFactory.send("device1", ProcessAdded(1, "package1", "process1"))
            fakeProcessTrackerFactory.send("device2", ProcessAdded(2, "package2", "process2"))
            advanceUntilIdle()

            deviceTracker.sendDeviceEvents(DeviceDisconnected(device1.serialNumber))
            advanceUntilIdle()

            assertThat(monitor.getProcessNames("device1", 1)).isNull()
            assertThat(monitor.getProcessNames("device2", 2))
                .isEqualTo(ProcessNames("package2", "process2"))
        }
    }

    @Test
    fun propagatesMaxProcessRetention(): Unit = runTest {
        FakeDeviceTracker().use { deviceTracker ->
            val monitor = processNameMonitor(deviceTracker, maxProcessRetention = 1)
            deviceTracker.sendDeviceEvents(DeviceOnline(mockDevice("device1")))
            advanceUntilIdle()

            fakeProcessTrackerFactory.send("device1", ProcessAdded(1, "package1", "process1"))
            fakeProcessTrackerFactory.send("device1", ProcessAdded(2, "package2", "process2"))
            fakeProcessTrackerFactory.send("device1", ProcessAdded(3, "package3", "process3"))
            advanceUntilIdle()
            fakeProcessTrackerFactory.send("device1", ProcessRemoved(1))
            fakeProcessTrackerFactory.send("device1", ProcessRemoved(2))
            advanceUntilIdle()

            assertThat(monitor.getProcessNames("device1", 1)).isNull()
            assertThat(monitor.getProcessNames("device1", 2))
                .isEqualTo(ProcessNames("package2", "process2"))
            assertThat(monitor.getProcessNames("device1", 3))
                .isEqualTo(ProcessNames("package3", "process3"))
        }
    }

    @Test
    fun disconnect_closesPerDeviceMonitor(): Unit = runTest {
        FakeDeviceTracker().use { deviceTracker ->
            val monitor = processNameMonitor(deviceTracker)
            val device = mockDevice("device1")
            deviceTracker.sendDeviceEvents(DeviceOnline(device))
            advanceTimeBy(2000) // Let the flow run a few cycles
            val perDeviceMonitor = monitor.devices["device1"]

            deviceTracker.sendDeviceEvents(DeviceDisconnected(device.serialNumber))
            advanceUntilIdle()

            assertThat(perDeviceMonitor?.scope?.isActive).isFalse()
        }
    }

    private fun CoroutineScope.processNameMonitor(
        deviceTracker: DeviceTracker<IDevice> = FakeDeviceTracker(),
        trackerFactory: ProcessTrackerFactory<IDevice> = fakeProcessTrackerFactory,
        maxProcessRetention: Int = 1000,
    ): ProcessNameMonitorImpl {
        return ProcessNameMonitorImpl(
            this,
            deviceTracker,
            trackerFactory,
            maxProcessRetention,
            FakeAdbLoggerFactory().logger
        ).apply {
            start()
        }
    }
}