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
package jp.co.gahojin.thrifty.integration.conformance.server

import com.sun.net.httpserver.HttpContext
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import jp.co.gahojin.thrifty.integration.kgen.ThriftTestProcessor
import jp.co.gahojin.thrifty.protocol.BinaryProtocol
import jp.co.gahojin.thrifty.protocol.CompactProtocol
import jp.co.gahojin.thrifty.protocol.JsonProtocol
import jp.co.gahojin.thrifty.protocol.Protocol
import jp.co.gahojin.thrifty.testing.ServerProtocol
import jp.co.gahojin.thrifty.transport.Transport
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class TestServer(private val protocol: ServerProtocol = ServerProtocol.BINARY) : Extension, BeforeEachCallback,
    AfterEachCallback {
    val processor = ThriftTestProcessor(ThriftTestHandler())
    private var server: HttpServer? = null

    class TestTransport(
        val b: Buffer = Buffer()
    ) : Transport {

        override fun read(buffer: ByteArray, offset: Int, count: Int) = b.read(buffer, offset, count)

        override fun write(buffer: ByteArray, offset: Int, count: Int) {
            b.write(buffer, offset, count)
        }

        override fun skip(count: Long) = b.skip(count)

        override fun flush() = b.flush()

        override fun close() = b.close()
    }

    private fun handleRequest(exchange: HttpExchange) {
        val inputTransport = TestTransport(Buffer().readFrom(exchange.requestBody))
        val outputTransport = TestTransport()

        val input = protocolFactory(inputTransport)
        val output = protocolFactory(outputTransport)

        runBlocking {
            processor.process(input, output)
        }

        exchange.sendResponseHeaders(200, outputTransport.b.size)
        exchange.responseBody.use {
            outputTransport.b.writeTo(it)
        }
    }

    fun run() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            val context: HttpContext = createContext("/")
            context.setHandler(::handleRequest)

            executor = Executors.newSingleThreadExecutor()
            start()
        }
    }

    fun port(): Int {
        return checkNotNull(server).address.port
    }

    override fun beforeEach(context: ExtensionContext) {
        run()
    }

    override fun afterEach(context: ExtensionContext) {
        cleanupServer()
    }

    fun close() {
        cleanupServer()
    }

    private fun cleanupServer() {
        server?.stop(0)
        server = null
    }

    private fun protocolFactory(transport: Transport): Protocol = when (protocol) {
        ServerProtocol.BINARY -> BinaryProtocol(transport)
        ServerProtocol.COMPACT -> CompactProtocol(transport)
        ServerProtocol.JSON -> JsonProtocol(transport)
    }
}
