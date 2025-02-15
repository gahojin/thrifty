/*
 * Thrifty
 *
 * Copyright (c) Microsoft Corporation
 * Copyright (c) GAHOJIN, Inc.
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
package com.microsoft.thrifty.service

import com.microsoft.thrifty.Struct
import com.microsoft.thrifty.protocol.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Closeable
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmOverloads

/**
 * Implements a basic service client that executes methods asynchronously.
 *
 * Note that, while the client-facing API of this class is callback-based,
 * the implementation itself is **blocking**.  Unlike the Apache
 * implementation, there is no presumption made here about framed encoding
 * at the transport level.  If your backend requires framing, be sure to
 * configure your [Protocol] and [com.microsoft.thrifty.transport.Transport]
 * objects appropriately.
 */
open class AsyncClientBase @JvmOverloads protected constructor(
    protocol: Protocol,
    private val listener: Listener,
    context: CoroutineContext = Dispatchers.IO,
) : ClientBase(protocol, context), Closeable {
    /**
     * Exposes important events in the client's lifecycle.
     */
    interface Listener {
        /**
         * Invoked when the client connection has been closed.
         *
         * After invocation, the client is no longer usable.  All subsequent
         * method call attempts will result in an immediate exception on the
         * calling thread.
         */
        fun onTransportClosed()

        /**
         * Invoked when a client-level error has occurred.
         *
         * This generally indicates a connectivity or protocol error,
         * and is distinct from errors returned as part of normal service
         * operation.
         *
         * The client is guaranteed to have been closed and shut down
         * by the time this method is invoked.
         *
         * @param error the throwable instance representing the error.
         */
        fun onError(error: Throwable)
    }

    private val lock = Mutex()

    /**
     * When invoked by a derived instance, places the given call in a queue to
     * be sent to the server.
     *
     * @param methodCall the remote method call to be invoked
     */
    protected fun <T> enqueue(methodCall: MethodCall<T>) {
        check(running.value) { "Cannot write to a closed service client" }

        scope.launch {
            lock.withLock {
                try {
                    val result = invokeRequest(methodCall)
                    methodCall.callback?.onSuccess(result)
                } catch (e: ServerException) {
                    methodCall.callback?.onError(e.thriftException)
                } catch (e: Exception) {
                    if (e is Struct) {
                        methodCall.callback?.onError(e)
                        return@withLock
                    }

                    close(e)
                    methodCall.callback?.onError(e)
                }
            }
        }
    }

    override fun close() = close(null)

    private fun close(error: Throwable?) {
        if (!running.compareAndSet(expect = true, update = false)) {
            return
        }
        closeProtocol()

        scope.launch {
            error?.also {
                listener.onError(it)
            } ?: run {
                listener.onTransportClosed()
            }
        }
    }
}
