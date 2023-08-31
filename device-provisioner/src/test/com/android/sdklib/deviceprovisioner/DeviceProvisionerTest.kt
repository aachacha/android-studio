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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceInfo.DeviceType
import com.google.wireless.android.sdk.stats.DeviceInfo.MdnsConnectionType
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.Test

/**
 * Tests the [DeviceProvisioner] and its two basic plugins, [PhysicalDeviceProvisionerPlugin] and
 * [DefaultProvisionerPlugin]
 */
class DeviceProvisionerTest {
  val fakeSession = FakeAdbSession()

  private val deviceIcons =
    DeviceIcons(EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT)
  private val plugin = PhysicalDeviceProvisionerPlugin(fakeSession.scope, deviceIcons)
  private val provisioner =
    DeviceProvisioner.create(fakeSession.scope, fakeSession, listOf(plugin), deviceIcons)

  object SerialNumbers {
    const val physicalUsb = "X1058A"
    const val physicalWifi = "adb-X1BQ704RX2B-VQ4ADB._adb-tls-connect._tcp."
    const val emulator = "emulator-5554"
  }

  init {
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.physicalUsb),
      mapOf(
        "ro.serialno" to SerialNumbers.physicalUsb,
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.physicalWifi),
      mapOf(
        "ro.serialno" to "X1BQ704RX2B",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "Pixel 6",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    fakeSession.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(SerialNumbers.emulator),
      mapOf(
        "ro.serialno" to "EMULATOR31X3X7X0",
        DevicePropertyNames.RO_BUILD_VERSION_SDK to "31",
        DevicePropertyNames.RO_PRODUCT_MANUFACTURER to "Google",
        DevicePropertyNames.RO_PRODUCT_MODEL to "sdk_goog3_x86_64",
        DevicePropertyNames.RO_SF_LCD_DENSITY to "320",
      )
    )
    for (serial in
      listOf(SerialNumbers.emulator, SerialNumbers.physicalWifi, SerialNumbers.physicalUsb)) {
      fakeSession.deviceServices.configureShellCommand(
        DeviceSelector.fromSerialNumber(serial),
        command = "wm size",
        stdout = "Physical size: 2000x1500\n"
      )
    }
  }

  private fun setDevices(vararg serialNumber: String) {
    fakeSession.hostServices.devices =
      DeviceList(serialNumber.map { DeviceInfo(it, DeviceState.ONLINE) }, emptyList())
  }

  @Test
  fun physicalUsbWiFiProperties() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalUsb, SerialNumbers.physicalWifi)

      // The plugin adds the devices one at a time, so there are two events here
      val handles =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(2)
          handles
        }

      val handlesByType = handles.associateBy { it.state.properties.connectionType }

      assertThat(handlesByType).hasSize(2)
      val usbHandle = checkNotNull(handlesByType[ConnectionType.USB])
      assertThat(usbHandle.state.connectedDevice?.serialNumber).isEqualTo(SerialNumbers.physicalUsb)
      usbHandle.state.properties.apply {
        assertThat(wearPairingId).isEqualTo(SerialNumbers.physicalUsb)
        assertThat(deviceInfoProto.mdnsConnectionType).isEqualTo(MdnsConnectionType.MDNS_NONE)
        checkPhysicalDeviceProperties()
      }

      val wifiHandle = checkNotNull(handlesByType[ConnectionType.WIFI])
      assertThat(wifiHandle.state.connectedDevice?.serialNumber)
        .isEqualTo(SerialNumbers.physicalWifi)
      wifiHandle.state.properties.apply {
        assertThat(wearPairingId).isEqualTo("X1BQ704RX2B")
        assertThat(deviceInfoProto.mdnsConnectionType)
          .isEqualTo(MdnsConnectionType.MDNS_AUTO_CONNECT_TLS)
        checkPhysicalDeviceProperties()
      }
    }
  }

  private fun DeviceProperties.checkPhysicalDeviceProperties() {
    assertThat(resolution).isEqualTo(Resolution(2000, 1500))
    assertThat(resolutionDp).isEqualTo(Resolution(1000, 750))

    deviceInfoProto.apply {
      assertThat(manufacturer).isEqualTo("Google")
      assertThat(model).isEqualTo("Pixel 6")
      assertThat(deviceType).isEqualTo(DeviceType.LOCAL_PHYSICAL)
      assertThat(deviceProvisionerId).isEqualTo(PhysicalDeviceProvisionerPlugin.PLUGIN_ID)
    }
  }

  @Test
  fun physicalDeviceMaintainsIdentityOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalUsb)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          assertThat(handle.state).isInstanceOf(Connected::class.java)

          handle
        }

      // We also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Disconnected::class.java)
      }

      setDevices(SerialNumbers.physicalUsb)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun unauthorizedPhysicalDevice() {
    val handles = Channel<List<DeviceHandle>>(1)
    val unclaimedDevices = Channel<List<ConnectedDevice>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { handles.send(it) } }
    fakeSession.scope.launch { provisioner.unclaimedDevices.collect { unclaimedDevices.send(it) } }

    runBlockingWithTimeout {
      // Show the device as unauthorized
      fakeSession.hostServices.devices =
        DeviceList(
          listOf(DeviceInfo(SerialNumbers.physicalUsb, DeviceState.UNAUTHORIZED)),
          emptyList()
        )

      unclaimedDevices.receiveUntilPassing { devices ->
        assertThat(devices).hasSize(1)

        val device = devices[0]
        assertThat(device.deviceInfo.deviceState).isEqualTo(DeviceState.UNAUTHORIZED)
      }

      // Now show the device as online
      setDevices(SerialNumbers.physicalUsb)

      unclaimedDevices.receiveUntilPassing { devices -> assertThat(devices).isEmpty() }

      handles.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle.state).isInstanceOf(Connected::class.java)
        assertThat(handle.state.properties.connectionType).isEqualTo(ConnectionType.USB)
      }
    }
  }

  @Test
  fun defaultDeviceIsDistinctOnReconnection() {
    val channel = Channel<List<DeviceHandle>>(1)
    fakeSession.scope.launch { provisioner.devices.collect { channel.send(it) } }

    runBlockingWithTimeout {
      setDevices(SerialNumbers.emulator)

      val originalHandle =
        channel.receiveUntilPassing { handles ->
          assertThat(handles).hasSize(1)

          val handle = handles[0]
          // assertThat(handle).isInstanceOf(DefaultDeviceHandle::class.java)
          assertThat(handle.state).isInstanceOf(Connected::class.java)

          handle
        }

      // Now we also want to update whenever the state changes
      fakeSession.scope.launch {
        originalHandle.stateFlow.collect { channel.send(provisioner.devices.value) }
      }

      setDevices()

      // Check this first, since changes to it don't result in a new message on `channel`
      yieldUntil { originalHandle.scope.coroutineContext.job.isCancelled }

      // We get two messages on the channel, one for the device becoming disconnected, and one
      // for the device list changing. We don't know what order they will occur in, but it
      // doesn't matter; just check the state after the second.
      channel.receiveUntilPassing { handles ->
        assertThat(handles).isEmpty()
        assertThat(originalHandle.state).isInstanceOf(Disconnected::class.java)
      }

      setDevices(SerialNumbers.emulator)

      channel.receiveUntilPassing { handles ->
        assertThat(handles).hasSize(1)

        val handle = handles[0]
        assertThat(handle).isNotSameAs(originalHandle)
        assertThat(handle.state).isInstanceOf(Connected::class.java)
      }
    }
  }

  @Test
  fun findConnectedDeviceHandle() {
    runBlockingWithTimeout {
      setDevices(SerialNumbers.physicalWifi, SerialNumbers.emulator)

      val emulator =
        async(Dispatchers.IO) {
          provisioner.findConnectedDeviceHandle(
            DeviceSelector.fromSerialNumber(SerialNumbers.emulator),
            Duration.ofSeconds(5)
          )
        }

      val handle = emulator.await()
      assertThat(handle?.state?.connectedDevice?.serialNumber).isEqualTo(SerialNumbers.emulator)
    }
  }
}
