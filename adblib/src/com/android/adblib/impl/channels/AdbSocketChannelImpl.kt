package com.android.adblib.impl.channels

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.adbLogger
import com.android.adblib.impl.remainingTimeoutToString
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

/**
 * Implementation of [AdbChannel] over an [AsynchronousSocketChannel] socket connection
 */
internal class AdbSocketChannelImpl(
    private val host: AdbSessionHost,
    private val socketChannel: AsynchronousSocketChannel
) : AdbChannel {

    private val logger = adbLogger(host)

    private val channelWriteHandler = object : ChannelWriteHandler(host, socketChannel) {
        override val supportsTimeout: Boolean
            get() = true

        override fun asyncWrite(
            buffer: ByteBuffer,
            timeout: Long,
            unit: TimeUnit,
            continuation: CancellableContinuation<Unit>,
            completionHandler: ContinuationCompletionHandler<Int>
        ) {
            socketChannel.write(buffer, timeout, unit, continuation, completionHandler)
        }
    }

    private val channelReadHandler = object : ChannelReadHandler(host, socketChannel) {
        override val supportsTimeout: Boolean
            get() = true

        override fun asyncRead(
            buffer: ByteBuffer,
            timeout: Long,
            unit: TimeUnit,
            continuation: CancellableContinuation<Unit>,
            completionHandler: ContinuationCompletionHandler<Int>
        ) {
            socketChannel.read(buffer, timeout, unit, continuation, completionHandler)
        }
    }

    /**
     * Tells whether the underlying [AsynchronousSocketChannel] is open.
     */
    internal val isOpen: Boolean
        get() = socketChannel.isOpen

    override fun toString(): String {
        val remoteAddress = try {
            socketChannel.remoteAddress
        } catch (e: ClosedChannelException) {
            "<channel-closed>"
        } catch (e: Throwable) {
            "<error: $e>"
        }
        return "AdbSocketChannelImpl(remote=$remoteAddress)"
    }

    @Throws(Exception::class)
    override fun close() {
        logger.debug { "Closing socket channel" }
        socketChannel.close()
    }

    suspend fun connect(address: InetSocketAddress, timeout: Long, unit: TimeUnit) {
        logger.debug {
            "Connecting to IP address $address, timeout=${remainingTimeoutToString(timeout, unit)}"
        }

        // Note: We use a local completion handler so that we can report the address in
        // case of failure.
        val connectCompletionHandler = object : ContinuationCompletionHandler<Void?>() {

            override fun completed(result: Void?) {
                logger.debug { "Connection completed successfully" }
            }

            override fun wrapError(e: Throwable): Throwable {
                return IOException("Error connecting channel to address '$address'", e)
            }
        }

        suspendChannelCoroutine<Unit>(host, socketChannel, timeout, unit) { continuation ->
            socketChannel.connect(address, continuation, connectCompletionHandler)
        }
    }

    override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        channelReadHandler.readBuffer(buffer, timeout, unit)
    }

    override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        channelReadHandler.readExactly(buffer, timeout, unit)
    }

    override suspend fun writeBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        channelWriteHandler.writeBuffer(buffer, timeout, unit)
    }

    override suspend fun writeExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        channelWriteHandler.writeExactly(buffer, timeout, unit)
    }

    override suspend fun shutdownInput() {
        withContext(host.ioDispatcher) {
            logger.debug { "Shutting down input channel" }
            @Suppress("BlockingMethodInNonBlockingContext")
            socketChannel.shutdownInput()
        }
    }

    override suspend fun shutdownOutput() {
        withContext(host.ioDispatcher) {
            logger.debug { "Shutting down output channel" }
            @Suppress("BlockingMethodInNonBlockingContext")
            socketChannel.shutdownOutput()
        }
    }
}
