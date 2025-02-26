/*
 * Thrifty
 *
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
package jp.co.gahojin.thrifty

import jp.co.gahojin.thrifty.transport.Transport
import java.io.InputStream
import java.io.OutputStream

/**
 * Creates a read-only transport from the given [InputStream].
 *
 * @receiver the inputstream underlying the new transport.
 * @return a read-only transport.
 */
fun <S : InputStream> S.transport() = object : Transport {
    private val self = this@transport

    override fun close() = self.close()

    override fun read(buffer: ByteArray, offset: Int, count: Int) = self.read(buffer, offset, count)

    override fun write(data: ByteArray) = error("read-only transport")

    override fun write(buffer: ByteArray, offset: Int, count: Int) = error("read-only transport")

    override fun skip(count: Long) = self.skipNBytes(count)

    override fun flush() = Unit
}

/**
 * Creates a write-only transport from the given [OutputStream]
 *
 * @receiver the outputstream underlying the new transport.
 * @return a write-only transport.
 */
fun <S : OutputStream> S.transport() = object : Transport {
    private val self = this@transport

    override fun close() = self.close()

    override fun read(buffer: ByteArray, offset: Int, count: Int) = error("write-only transport")

    override fun write(data: ByteArray) { self.write(data) }

    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        self.write(buffer, offset, count)
    }

    override fun skip(count: Long) = Unit

    override fun flush() = self.flush()
}
