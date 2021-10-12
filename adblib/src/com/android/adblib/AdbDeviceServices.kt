package com.android.adblib

import com.android.adblib.impl.ShellWithIdleMonitoring
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.MultiLineShellCollector
import com.android.adblib.utils.TextShellCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeoutException

const val DEFAULT_SHELL_BUFFER_SIZE = 8_192

/**
 * Exposes services that are executed by the ADB daemon of a given device
 */
interface AdbDeviceServices {
    /**
     * Returns a [Flow] that, when collected, executes a shell command on a device
     * ("<device-transport>:shell" query) and emits the output of the command to the [Flow].
     *
     * The returned [Flow] elements are collected and emitted through a [ShellCollector],
     * which enables advanced use cases for collecting, mapping, filtering and joining
     * the command output which is initially collected as [ByteBuffer]. A typical use
     * case is to use a [ShellCollector] that decodes the output as a [Flow] of [String],
     * one for each line of the output.
     *
     * The flow is active until an exception is thrown, cancellation is requested by
     * the flow consumer, or the shell command is terminated.
     *
     * The flow can throw [AdbProtocolErrorException], [AdbFailResponseException],
     * [IOException] or any [Exception] thrown by [shellCollector]
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [command] the shell command to execute
     * @param [shellCollector] The [ShellCollector] invoked to collect the shell command output
     *   and emit elements to the resulting [Flow]
     * @param [stdinChannel] is an optional [AdbChannel] providing bytes to send to the `stdin`
     *   of the shell command
     * @param [commandTimeout] timeout tracking the command execution, tracking starts *after* the
     *   device connection has been successfully established. If the command takes more time than
     *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
     * @param [bufferSize] the size of the buffer used to receive data from the shell command output
     */
    fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
    ): Flow<T>

    /**
     * Opens a `sync` session on a device ("<device-transport>:sync" query) and returns
     * an instance of [AdbDeviceSyncServices] that allows performing one or more file
     * transfer operation with a device.
     *
     * The [AdbDeviceSyncServices] instance should be [closed][AutoCloseable.close]
     * when no longer in use, to ensure the underlying connection to the device is
     * closed.
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     */
    suspend fun sync(device: DeviceSelector): AdbDeviceSyncServices
}

/**
 * Similar to [AdbDeviceServices.shell] but captures the command output as a single
 * string, decoded using the [AdbProtocolUtils.ADB_CHARSET]&nbsp;[Charset] character set.
 *
 * Note: This method should be used only for commands that output a relatively small
 * amount of text.
 */
suspend fun AdbDeviceServices.shellAsText(
    device: DeviceSelector,
    command: String,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): String {
    val collector = TextShellCollector()
    return shell(device, command, collector, stdinChannel, commandTimeout, bufferSize).first()
}

/**
 * Similar to [AdbDeviceServices.shell] but captures the command output as a [Flow]
 * of [String], with one string for each line of the output.
 *
 * Lines are decoded using the [AdbProtocolUtils.ADB_CHARSET]&nbsp;[Charset], and line
 * terminators are detected using the [AdbProtocolUtils.ADB_NEW_LINE] character.
 *
 * Note: Each line is emitted to the flow as soon as it is received, so this method
 *       can be used to "stream" the output of a shell command without waiting for the
 *       command to terminate.
 */
fun AdbDeviceServices.shellAsLines(
    device: DeviceSelector,
    command: String,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<String> {
    val collector = MultiLineShellCollector()
    return shell(device, command, collector, stdinChannel, commandTimeout, bufferSize)
}

/**
 * Same as [AdbDeviceServices.shell] except that a [TimeoutException] is thrown
 * when the command does not generate any output for the [Duration] specified in
 * [commandOutputTimeout].
 */
fun <T> AdbDeviceServices.shellWithIdleMonitoring(
    host: AdbLibHost,
    device: DeviceSelector,
    command: String,
    stdoutCollector: ShellCollector<T>,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    commandOutputTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<T> {
    return ShellWithIdleMonitoring(
        host,
        this,
        device,
        command,
        stdoutCollector,
        stdinChannel,
        commandTimeout,
        commandOutputTimeout,
        bufferSize
    ).flow()
}

/**
 * Uploads a single file to a remote device transferring the contents of [sourceChannel].
 *
 * @see [AdbDeviceSyncServices.send]
 */
suspend fun AdbDeviceServices.syncSend(
    device: DeviceSelector,
    sourceChannel: AdbInputChannel,
    remoteFilePath: String,
    remoteFileMode: RemoteFileMode,
    remoteFileTime: FileTime,
    progress: SyncProgress,
    bufferSize: Int = SYNC_DATA_MAX
) {
    sync(device).use {
        it.send(
            sourceChannel,
            remoteFilePath,
            remoteFileMode,
            remoteFileTime,
            progress,
            bufferSize
        )
    }
}

/**
 * Uploads a single file to a remote device transferring the contents of [sourcePath].
 *
 * @see [AdbDeviceSyncServices.send]
 */
suspend fun AdbDeviceServices.syncSend(
    session: AdbLibSession,
    device: DeviceSelector,
    sourcePath: Path,
    remoteFilePath: String,
    remoteFileMode: RemoteFileMode,
    remoteFileTime: FileTime,
    progress: SyncProgress,
    bufferSize: Int = SYNC_DATA_MAX
) {
    session.channelFactory.openFile(sourcePath).use { source ->
        syncSend(
            device,
            source,
            remoteFilePath,
            remoteFileMode,
            remoteFileTime,
            progress,
            bufferSize
        )
        source.close()
    }
}

/**
 * Retrieves a single file from a remote device and writes its contents to a [destinationChannel].
 *
 * @see [AdbDeviceSyncServices.recv]
 */
suspend fun AdbDeviceServices.syncRecv(
    device: DeviceSelector,
    remoteFilePath: String,
    destinationChannel: AdbOutputChannel,
    progress: SyncProgress,
    bufferSize: Int = SYNC_DATA_MAX
) {
    sync(device).use {
        it.recv(
            remoteFilePath,
            destinationChannel,
            progress,
            bufferSize
        )
    }
}

/**
 * Retrieves a single file from a remote device and writes its contents to a [destinationPath].
 *
 * @see [AdbDeviceSyncServices.recv]
 */
suspend fun AdbDeviceServices.syncRecv(
    session: AdbLibSession,
    device: DeviceSelector,
    remoteFilePath: String,
    destinationPath: Path,
    progress: SyncProgress,
    bufferSize: Int = SYNC_DATA_MAX
) {
    session.channelFactory.createFile(destinationPath).use { destination ->
        syncRecv(
            device,
            remoteFilePath,
            destination,
            progress,
            bufferSize
        )
        destination.close()
    }
}
