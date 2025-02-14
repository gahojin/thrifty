/*
 * Thrifty
 *
 * Copyright (c) Microsoft Corporation
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED ON AN  *AS IS* BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
 * WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABLITY OR NON-INFRINGEMENT.
 *
 * See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package com.microsoft.thrifty.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketOptions
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmOverloads

class SocketTransport @JvmOverloads constructor(
    private val host: String,
    private val port: Int,
    private val enableTls: Boolean = false,
    private val context: CoroutineContext = Dispatchers.IO,
    private val socketOptions: SocketOptions.TCPClientSocketOptions.() -> Unit = {
        keepAlive = true
        noDelay = true
        reuseAddress = false
        reusePort = false
    },
) : Transport {
    private lateinit var selectorManager: SelectorManager
    private lateinit var socket: Socket
    private lateinit var readChannel: ByteReadChannel
    private lateinit var writeChannel: ByteWriteChannel

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        return runBlocking {
            readChannel.readFully(
                out =  buffer,
                start = offset,
                end =  offset + count,
            )
            count
        }
    }

     override fun write(buffer: ByteArray, offset: Int, count: Int) {
         runBlocking {
            writeChannel.writeFully(
                value = buffer,
                startIndex = offset,
                endIndex = offset + count,
            )
        }
    }

    override fun flush() {
        runBlocking {
            writeChannel.flush()
        }
    }

    fun connect() {
        runBlocking {
            selectorManager = SelectorManager(context)
            socket = aSocket(selectorManager).tcp().connect(host, port, socketOptions)

            if (enableTls) {
                socket = socket.tls(context)
            }
            readChannel = socket.openReadChannel()
            writeChannel = socket.openWriteChannel(autoFlush = false)
        }
    }

    override fun close() {
        writeChannel.close(null)
        socket.close()
        selectorManager.close()
    }

    class Builder(
        var host: String,
        val port: Int,
    ) {
        private var readTimeout: Long = Long.MAX_VALUE
        private var enableTls: Boolean = false

        fun readTimeout(value: Long): Builder = apply {
            readTimeout = value
        }

        fun enableTls(value: Boolean): Builder = apply {
            enableTls = value
        }

        fun build(): SocketTransport {
            return SocketTransport(host, port, enableTls) {
                keepAlive = true
                noDelay = true
                reuseAddress = false
                reusePort = true
                socketTimeout = this@Builder.readTimeout
            }
        }
    }
}
