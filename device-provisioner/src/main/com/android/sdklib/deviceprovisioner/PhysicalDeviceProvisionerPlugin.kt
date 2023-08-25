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
import com.android.adblib.deviceProperties
import com.android.adblib.serialNumber
import com.android.adblib.utils.createChildScope
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Plugin providing access to physical devices, connected over USB or WiFi. */
class PhysicalDeviceProvisionerPlugin(
  val scope: CoroutineScope,
  private val deviceIcons: DeviceIcons
) : DeviceProvisionerPlugin {

  companion object {
    const val PLUGIN_ID = "PhysicalDevice"
  }
  override val priority = 0

  private val devicesBySerial = hashMapOf<String, PhysicalDeviceHandle>()

  private val _devices = MutableStateFlow(emptyList<DeviceHandle>())
  override val devices = _devices.asStateFlow()

  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    val properties = device.deviceProperties().all().asMap()

    val deviceProperties =
      DeviceProperties.build {
        readAdbSerialNumber(device.serialNumber)
        readCommonProperties(properties)
        populateDeviceInfoProto(PLUGIN_ID, device.serialNumber, properties)
        if (connectionType != ConnectionType.WIFI) {
          connectionType = ConnectionType.USB
        }
        resolution = Resolution.readFromDevice(device)
        icon =
          when (deviceType) {
            DeviceType.HANDHELD -> deviceIcons.handheld
            DeviceType.WEAR -> deviceIcons.wear
            DeviceType.TV -> deviceIcons.tv
            DeviceType.AUTOMOTIVE -> deviceIcons.automotive
            else -> deviceIcons.handheld
          }
      }

    val serialNumber = checkNotNull(properties["ro.serialno"]) { "Missing [ro.serialno] property" }

    // We want to be fairly confident this is a physical device; we expect USB-connected devices
    // to have their ADB serial number match their device serial number.
    val isUsb = deviceProperties.connectionType == ConnectionType.USB
    if (isUsb && serialNumber != device.serialNumber) {
      return null
    }
    // If a system property says it's virtual, it probably is.
    if (deviceProperties.isVirtual == true) {
      return null
    }

    val newState = Connected(deviceProperties, device)
    val handle =
      checkNotNull(
        devicesBySerial.compute(serialNumber) { _, handle ->
          when (handle) {
            null ->
              // Physical devices normally live as long as the plugin, since we remember offline
              // devices, however they can be explicitly deleted by the user.
              PhysicalDeviceHandle(
                scope.createChildScope(isSupervisor = true),
                MutableStateFlow(newState)
              )
            else -> handle.also { it.stateFlow.value = newState }
          }
        }
      )

    updateDevices()

    scope.launch {
      // Update device state on termination. We keep it around in case it reconnects.
      device.awaitDisconnection()
      handle.stateFlow.value = Disconnected(handle.state.properties)
    }
    return handle
  }

  private fun updateDevices() {
    _devices.value = devicesBySerial.values.toList()
  }
}

private class PhysicalDeviceHandle(
  override val scope: CoroutineScope,
  override val stateFlow: MutableStateFlow<DeviceState>
) : DeviceHandle
