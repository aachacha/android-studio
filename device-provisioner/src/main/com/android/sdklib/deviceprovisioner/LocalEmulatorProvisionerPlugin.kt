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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.deviceProperties
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.EmulatorConsole
import com.android.adblib.tools.localConsoleAddress
import com.android.adblib.tools.openEmulatorConsole
import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.GuardedBy
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage.ANDROID_TV_TAG
import com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_PLAY_STORE_TAG
import com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_TAG
import com.android.sdklib.repository.targets.SystemImage.GOOGLE_TV_TAG
import com.android.sdklib.repository.targets.SystemImage.WEAR_TAG
import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Provides access to emulators running on the local machine from the standard AVD directory.
 * Supports creating, editing, starting, and stopping AVD instances.
 *
 * This plugin creates device handles for all AVDs present in the standard AVD directory, running or
 * not. The AVD path is used to identify devices and establish the link between connected devices
 * and their handles. The directory is periodically rescanned to find new devices, and immediately
 * rescanned after an edit is made via a device action.
 */
class LocalEmulatorProvisionerPlugin(
  private val adbSession: AdbSession,
  private val avdManager: AvdManager,
  rescanPeriod: Duration = Duration.ofSeconds(10)
) : DeviceProvisionerPlugin {
  val logger = thisLogger(adbSession)

  /**
   * An abstraction of the AvdManager / AvdManagerConnection classes to be injected, allowing for
   * testing and decoupling from Studio.
   */
  interface AvdManager {
    suspend fun rescanAvds(): List<AvdInfo>
    suspend fun createAvd(): Boolean
    suspend fun editAvd(avdInfo: AvdInfo): Boolean
    suspend fun startAvd(avdInfo: AvdInfo)
    suspend fun stopAvd(avdInfo: AvdInfo)
  }

  private val scope = adbSession.scope
  init {
    scope.coroutineContext.job.invokeOnCompletion {
      avdScanner.cancel()
      emulatorConsoles.values.forEach { it.close() }
    }
  }

  // We can identify local emulators reliably, so this can be relatively high priority.
  override val priority = 100

  private val mutex = Mutex()
  @GuardedBy("mutex") private val deviceHandles = HashMap<Path, LocalEmulatorDeviceHandle>()

  private val _devices = MutableStateFlow<List<DeviceHandle>>(emptyList())
  override val devices: StateFlow<List<DeviceHandle>> = _devices.asStateFlow()

  private val emulatorConsoles = ConcurrentHashMap<ConnectedDevice, EmulatorConsole>()

  // TODO: Consider if it would be better to use a filesystem watcher here instead of polling.
  private val avdScanner =
    PeriodicAction(scope, rescanPeriod) {
      val avdsOnDisk = avdManager.rescanAvds().associateBy { it.dataFolderPath }
      mutex.withLock {
        // Remove any current DeviceHandles that are no longer present on disk, unless they are
        // connected. (If a client holds on to the disconnected device handle, and it gets
        // recreated with the same path, the client will get a new device handle, which is fine.)
        deviceHandles.entries.removeIf { (path, handle) ->
          !avdsOnDisk.containsKey(path) && handle.state is Disconnected
        }

        for ((path, avdInfo) in avdsOnDisk) {
          when (val handle = deviceHandles[path]) {
            null ->
              deviceHandles[path] =
                LocalEmulatorDeviceHandle(
                  scope.createChildScope(isSupervisor = true),
                  Disconnected(toDeviceProperties(avdInfo)),
                  avdInfo
                )
            else ->
              // Update the avdInfo if we're not currently running. If we are running, the old
              // values are probably still in effect, but we will update on the next scan after
              // shutdown.
              if (handle.avdInfo != avdInfo && handle.state is Disconnected) {
                handle.avdInfo = avdInfo
                handle.stateFlow.value = Disconnected(toDeviceProperties(avdInfo))
              }
          }
        }

        _devices.value = deviceHandles.values.toList()
      }
    }

  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    val result = LOCAL_EMULATOR_REGEX.matchEntire(device.serialNumber) ?: return null
    val port = result.groupValues[1].toIntOrNull() ?: return null

    val emulatorConsole =
      adbSession.openEmulatorConsole(
        localConsoleAddress(port),
        AndroidLocationsSingleton.userHomeLocation.resolve(".emulator_console_auth_token")
      )
    emulatorConsoles[device] = emulatorConsole

    // This will fail on emulator versions prior to 30.0.18.
    val pathResult = kotlin.runCatching { emulatorConsole.avdPath() }
    val path = pathResult.getOrNull()

    if (path == null) {
      // If we can't connect to the emulator console, this isn't operationally a local emulator
      logger.debug { "Unable to read path for device ${device.serialNumber} from emulator console" }
      emulatorConsoles.remove(device)?.close()
      return null
    }

    // Try to link this device to an existing handle.
    var handle = tryConnect(path, port, device)
    if (handle == null) {
      // We didn't read this path from disk yet. Rescan and try again.
      avdScanner.runNow().join()
      handle = tryConnect(path, port, device)
    }
    if (handle == null) {
      // Apparently this emulator is not on disk, or it is not in the directory that we scan for
      // AVDs. (Perhaps GMD or Crow failed to pick it up.)
      logger.debug { "Unexpected device at $path" }
      emulatorConsoles.remove(device)?.close()
      return null
    }

    // We need to make sure that emulators change to Disconnected state once they are terminated.
    device.invokeOnDisconnection {
      handle.stateFlow.value = Disconnected(handle.state.properties)
      logger.debug { "Device ${device.serialNumber} closed; disconnecting from console" }
      emulatorConsoles.remove(device)?.close()
    }

    logger.debug { "Linked ${device.serialNumber} to AVD at $path" }

    return handle
  }

  private suspend fun tryConnect(
    path: Path,
    port: Int,
    device: ConnectedDevice
  ): LocalEmulatorDeviceHandle? =
    mutex.withLock {
      val handle = deviceHandles[path] ?: return@withLock null
      // For the offline device, we got most properties from the AvdInfo, though we had to
      // compute androidRelease. Now read them from the device.
      val deviceProperties = device.deviceProperties().allReadonly()
      val properties =
        LocalEmulatorProperties.build {
          readCommonProperties(deviceProperties)
          avdName = handle.avdInfo.name
          displayName = handle.avdInfo.displayName
          disambiguator = port.toString()
        }
      handle.stateFlow.value = Connected(properties, device)
      handle
    }

  private fun toDeviceProperties(avdInfo: AvdInfo) =
    LocalEmulatorProperties.build {
      manufacturer = avdInfo.deviceManufacturer
      model = avdInfo.deviceName
      androidVersion = avdInfo.androidVersion
      androidRelease = SdkVersionInfo.getVersionString(avdInfo.androidVersion.apiLevel)
      abi = Abi.getEnum(avdInfo.abiType)
      avdName = avdInfo.name
      displayName = avdInfo.displayName
      deviceType = avdInfo.tag.toDeviceType()
    }

  private fun IdDisplay.toDeviceType(): DeviceType =
    when (this) {
      ANDROID_TV_TAG, GOOGLE_TV_TAG -> DeviceType.TV
      AUTOMOTIVE_TAG, AUTOMOTIVE_PLAY_STORE_TAG -> DeviceType.AUTOMOTIVE
      WEAR_TAG -> DeviceType.WEAR
      else -> DeviceType.HANDHELD
    }

  private fun refreshDevices() {
    avdScanner.runNow()
  }

  override val createDeviceAction =
    object : CreateDeviceAction {
      override val label = "Create AVD"
      override val isEnabled = MutableStateFlow(true).asStateFlow()

      override suspend fun create() {
        if (avdManager.createAvd()) {
          refreshDevices()
        }
      }
    }

  /**
   * A handle for a local AVD stored in the SDK's AVD directory. These are only created when reading
   * an AVD off the disk; only devices that have already been read from disk will be claimed.
   */
  private inner class LocalEmulatorDeviceHandle(
    override val scope: CoroutineScope,
    initialState: DeviceState,
    initialAvdInfo: AvdInfo
  ) : DeviceHandle {
    override val stateFlow = MutableStateFlow(initialState)

    private val _avdInfo = AtomicReference(initialAvdInfo)

    /** AvdInfo can be updated when the device is edited on-disk and rescanned. */
    var avdInfo: AvdInfo
      get() = _avdInfo.get()
      set(value) = _avdInfo.set(value)

    /** The emulator console is present when the device is connected. */
    val emulatorConsole: EmulatorConsole?
      get() = state.connectedDevice?.let { emulatorConsoles[it] }

    override val activationAction =
      object : ActivationAction {
        override val label: String = "Start"

        override val isEnabled =
          stateFlow
            .map { it is Disconnected && !it.isTransitioning }
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

        override suspend fun activate(params: ActivationParams) {
          try {
            withContext(scope.coroutineContext) {
              stateFlow.advanceStateWithTimeout(
                timeout = CONNECTION_TIMEOUT,
                updateState = {
                  (it as? Disconnected)?.let {
                    Disconnected(it.properties, isTransitioning = true, status = "Starting up")
                  }
                },
                advanceAction = { avdManager.startAvd(avdInfo) }
              )
            }
          } catch (e: TimeoutCancellationException) {
            logger.warn("Emulator failed to connect within $CONNECTION_TIMEOUT_MINUTES minutes")
          }
        }
      }

    override val editAction =
      object : EditAction {
        override val label = "Edit"

        override val isEnabled = MutableStateFlow(true).asStateFlow()

        override suspend fun edit() {
          if (avdManager.editAvd(avdInfo)) {
            refreshDevices()
          }
        }
      }

    override val deactivationAction: DeactivationAction =
      object : DeactivationAction {
        override val label = "Stop"

        // We could check this with AvdManagerConnection.isAvdRunning, but that's expensive, and if
        // it's not running we should see it from ADB anyway
        override val isEnabled =
          stateFlow
            .map { it is Connected && !it.isTransitioning }
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)

        override suspend fun deactivate() {
          try {
            withContext(scope.coroutineContext) {
              stateFlow.advanceStateWithTimeout(
                timeout = DISCONNECTION_TIMEOUT,
                updateState = {
                  // TODO: In theory, we could cancel from the Connecting state, but that would
                  // require a lot of work in AvdManagerConnection to make everything shutdown
                  // cleanly.
                  (it as? Connected)?.let {
                    Connected(
                      it.properties,
                      isTransitioning = true,
                      status = "Shutting down",
                      connectedDevice = it.connectedDevice
                    )
                  }
                },
                advanceAction = ::stop
              )
            }
          } catch (e: TimeoutCancellationException) {
            logger.warn(
              "Emulator failed to disconnect within $DISCONNECTION_TIMEOUT_MINUTES minutes"
            )
          }
        }
      }

    /**
     * Attempts to stop the AVD. We can either use the emulator console or AvdManager (which uses a
     * shell command to kill the process)
     */
    private suspend fun stop() {
      emulatorConsole?.let {
        try {
          it.kill()
          return
        } catch (e: IOException) {
          // Connection to emulator console is closed, possibly due to a harmless race condition.
          logger.debug(e) { "Failed to shutdown via emulator console; falling back to AvdManager" }
        }
      }
      avdManager.stopAvd(avdInfo)
    }
  }
}

class LocalEmulatorProperties(
  base: DeviceProperties,
  val avdName: String,
  val displayName: String
) : DeviceProperties by base {

  override val title = displayName

  companion object {
    fun build(block: Builder.() -> Unit) =
      Builder().apply { isVirtual = true }.apply(block).run {
        LocalEmulatorProperties(buildBase(), checkNotNull(avdName), checkNotNull(displayName))
      }
  }

  class Builder : DeviceProperties.Builder() {
    var avdName: String? = null
    var displayName: String? = null
  }
}

private val LOCAL_EMULATOR_REGEX = "emulator-(\\d+)".toRegex()

private const val CONNECTION_TIMEOUT_MINUTES: Long = 5
private val CONNECTION_TIMEOUT = Duration.ofMinutes(CONNECTION_TIMEOUT_MINUTES)

private const val DISCONNECTION_TIMEOUT_MINUTES: Long = 1
private val DISCONNECTION_TIMEOUT = Duration.ofMinutes(DISCONNECTION_TIMEOUT_MINUTES)
