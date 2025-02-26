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
package jp.co.gahojin.thrifty.transport

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ProtocolException
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.mutableMapOf
import kotlin.collections.set

class HttpTransport(
    private val url: Url,
    private val httpClient: HttpClient,
) : Transport {
    private val customHeaders = mutableMapOf<String, String>()
    private val sendBuffer = Buffer()
    private lateinit var readChannel: ByteReadChannel

    constructor(url: String, httpClient: HttpClient) : this(Url(url), httpClient)

    fun send(data: ByteArray) = runBlocking {
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

    fun setCustomHeaders(headers: Map<String, String>) {
        customHeaders.clear()
        customHeaders.putAll(headers)
    }

    fun setCustomHeader(key: String, value: String) {
        customHeaders[key] = value
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
        send(sendBuffer.readByteArray())
    }

    override fun skip(count: Long) {
        sendBuffer.skip(count)
    }

    override fun close() {
        httpClient.close()
    }
}
