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
package jp.co.gahojin.thrifty.protocol

import io.kotest.assertions.fail
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.contain
import jp.co.gahojin.thrifty.TType
import jp.co.gahojin.thrifty.transport.BufferTransport
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.IOException
import okio.ProtocolException
import kotlin.math.PI
import kotlin.test.Test

class BinaryProtocolTest {
    @Test
    fun readString() {
        val buffer = Buffer()
        buffer.writeInt(3)
        buffer.writeUtf8("foo")
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.readString() shouldBe "foo"
    }

    @Test
    fun readStringGreaterThanLimit() {
        val buffer = Buffer()
        buffer.writeInt(13)
        buffer.writeUtf8("foobarbazquux")
        val proto = BinaryProtocol(BufferTransport(buffer), 12)
        try {
            proto.readString()
            fail("Expected a ProtocolException")
        } catch (e: ProtocolException) {
            e.message should contain("String size limit exceeded")
        }
    }

    @Test
    fun readBinary() {
        val buffer = Buffer()
        buffer.writeInt(4)
        buffer.writeUtf8("abcd")
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.readBinary() shouldBe "abcd".encodeUtf8()
    }

    @Test
    fun readBinaryGreaterThanLimit() {
        val buffer = Buffer()
        buffer.writeInt(6)
        buffer.writeUtf8("kaboom")
        val proto = BinaryProtocol(BufferTransport(buffer), 4)
        try {
            proto.readBinary()
            fail("Expected a ProtocolException")
        } catch (e: ProtocolException) {
            e.message should contain("Binary size limit exceeded")
        }
    }

    @Test
    fun readMessage() {
        val name = "foo"
        val buffer = Buffer()
        buffer.writeInt(name.encodeToByteArray().size)
        buffer.writeUtf8(name)
        buffer.writeByte(1)
        buffer.writeInt(1)
        val proto = BinaryProtocol(transport = BufferTransport(buffer))

        val messageData = proto.readMessageBegin()
        messageData.name shouldBe "foo"
        messageData.type shouldBe 1
        messageData.seqId shouldBe 1
    }

    @Test
    fun readMessageStrict() {
        val name = "foo"
        val buffer = Buffer()
        buffer.writeInt(-0x7FFEFFFF)
        buffer.writeInt(name.encodeToByteArray().size)
        buffer.writeUtf8(name)
        buffer.writeInt(1)
        val proto = BinaryProtocol(
            transport = BufferTransport(buffer),
            strictRead = true,
        )
        val messageData = proto.readMessageBegin()
        messageData.name shouldBe "foo"
        messageData.type shouldBe 1
        messageData.seqId shouldBe 1
    }

    @Test
    fun readMessageStrictMissingVersion() {
        val name = "foo"
        val buffer = Buffer()
        buffer.writeInt(name.encodeToByteArray().size)
        buffer.writeUtf8(name)
        buffer.writeInt(1)
        val proto = BinaryProtocol(
            transport = BufferTransport(buffer),
            strictRead = true,
        )

        val error = runCatching {
            proto.readMessageBegin()
        }.exceptionOrNull() as ProtocolException
        error.message shouldBe "Missing version in readMessageBegin"
    }

    @Test
    fun readMessageStrictInvalidVersion() {
        val name = "foo"
        val buffer = Buffer()
        buffer.writeInt(-0xFF)
        buffer.writeInt(name.encodeToByteArray().size)
        buffer.writeUtf8(name)
        buffer.writeInt(1)
        val proto = BinaryProtocol(
            transport = BufferTransport(buffer),
            strictRead = true,
        )

        val error = runCatching {
            proto.readMessageBegin()
        }.exceptionOrNull() as ProtocolException
        error.message shouldBe "Bad version in readMessageBegin"
    }

    @Test
    fun writeByte() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.writeByte(127.toByte())
        buffer.readByte() shouldBe 127.toByte()
    }

    @Test
    fun writeI16() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.writeI16(Short.MAX_VALUE)
        buffer.readShort() shouldBe Short.MAX_VALUE

        // Make sure it's written big-endian
        buffer.clear()
        proto.writeI16(0xFF00.toShort())
        buffer.readShort() shouldBe 0xFF00.toShort()
    }

    @Test
    fun writeI32() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.writeI32(-0xf0ff01)
        buffer.readInt() shouldBe -0xf0ff01
    }

    @Test
    fun writeI64() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.writeI64(0x12345678)

        buffer.readLong() shouldBe 0x12345678
    }

    @Test
    fun writeDouble() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))

        // Doubles go on the wire as the 8-byte blobs from
        // Double#doubleToLongBits().
        proto.writeDouble(PI)
        buffer.readLong() shouldBe PI.toBits()
    }

    @Test
    fun writeString() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.writeString("here is a string")
        buffer.readInt() shouldBe 16
        buffer.readUtf8() shouldBe "here is a string"
    }

    @Test
    fun writeMessage() {
        val buffer = Buffer()
        val proto = BinaryProtocol(BufferTransport(buffer))
        proto.writeMessageBegin(
            name = "foo",
            typeId = 1,
            seqId = 1,
        )

        buffer.readInt() shouldBe 3
        buffer.readUtf8(3) shouldBe "foo"
        buffer.readByte() shouldBe 1
        buffer.readInt() shouldBe 1
    }

    @Test
    fun writeMessageStrict() {
        val buffer = Buffer()
        val proto = BinaryProtocol(
            transport = BufferTransport(buffer),
            strictWrite = true,
        )
        proto.writeMessageBegin(
            name = "foo",
            typeId = 1,
            seqId = 1,
        )
        buffer.readInt() shouldBe -0x7FFEFFFF
        buffer.readInt() shouldBe 3
        buffer.readUtf8(3) shouldBe "foo"
        buffer.readInt() shouldBe 1
    }

    @Test
    fun adapterTest() {
        // This test case comes from actual data, and is intended
        // to ensure in particular that readers don't grab more data than
        // they are supposed to.
        val payload = "030001000600" +
                "0200030600030002" +
                "0b00040000007f08" +
                "0001000001930600" +
                "0200a70b00030000" +
                "006b0e00010c0000" +
                "000206000100020b" +
                "0002000000243030" +
                "3030303030302d30" +
                "3030302d30303030" +
                "2d303030302d3030" +
                "3030303030303030" +
                "3031000600010001" +
                "0b00020000002430" +
                "613831356232312d" +
                "616533372d343966" +
                "622d616633322d31" +
                "3636363261616366" +
                "62333300000000"
        val binaryData: ByteString = payload.decodeHex()
        val buffer = Buffer()
        buffer.write(binaryData)
        val protocol = BinaryProtocol(BufferTransport(buffer))
        read(protocol)
    }

    @Throws(IOException::class)
    fun read(protocol: Protocol) {
        protocol.readStructBegin()
        while (true) {
            val field = protocol.readFieldBegin()
            if (field.typeId == TType.STOP) {
                break
            }
            when (field.fieldId.toInt()) {
                1 -> if (field.typeId == TType.BYTE) {
                    protocol.readByte()
                } else {
                    protocol.skip(field.typeId)
                }

                2 -> if (field.typeId == TType.I16) {
                    protocol.readI16()
                } else {
                    protocol.skip(field.typeId)
                }

                3 -> if (field.typeId == TType.I16) {
                    protocol.readI16()
                } else {
                    protocol.skip(field.typeId)
                }

                4 -> if (field.typeId == TType.STRING) {
                    protocol.readBinary()
                } else {
                    protocol.skip(field.typeId)
                }

                else -> protocol.skip(field.typeId)
            }
            protocol.readFieldEnd()
        }
    }
}
