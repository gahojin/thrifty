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

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ProtocolException
import kotlin.coroutines.CoroutineContext

class HttpTransport(
    private val url: Url,
    private val httpClient: HttpClient,
    private val context: CoroutineContext = Dispatchers.IO,
) : Transport {
    private val customHeaders = mutableMapOf<String, String>()
    private val sendBuffer = Buffer()
    private lateinit var readChannel: ByteReadChannel

    constructor(url: String, httpClient: HttpClient) : this(Url(url), httpClient)

    suspend fun send(data: ByteArray) {
        withContext(context) {
            val response = httpClient.post(url) {
                method = HttpMethod.Post
                headers.append("Content-Type", "application/x-thrift")
                headers.append("Accept", "application/x-thrift")
                headers.append("User-Agent", "Java/THttpClient")
                for ((key, value) in customHeaders) {
                    headers.append(key, value)
                }
                setBody(ByteArrayContent(data))
            }

            val responseCode = response.status
            if (responseCode != HttpStatusCode.OK) {
                throw ProtocolException("HTTP Response code: $responseCode")
            }

            readChannel = response.bodyAsChannel()
        }
    }

    fun setCustomHeaders(headers: Map<String, String>) {
        customHeaders.clear()
        customHeaders.putAll(headers)
    }

    fun setCustomHeader(key: String, value: String) {
        customHeaders[key] = value
    }

    override fun close() {
        httpClient.close()
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        return runBlocking {
            readChannel.readAvailable(buffer, offset, count)
        }
    }

    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        sendBuffer.write(buffer, offset, count)
    }

    override fun flush() {
        runBlocking {
            send(sendBuffer.readByteArray())
        }
    }
}
