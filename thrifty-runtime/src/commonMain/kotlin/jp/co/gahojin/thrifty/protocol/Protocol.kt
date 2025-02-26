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

import jp.co.gahojin.thrifty.TType
import okio.ByteString
import okio.Closeable
import okio.IOException
import okio.ProtocolException
import kotlin.Byte

interface Protocol : Closeable {

    @Throws(IOException::class)
    fun writeMessageBegin(name: String, typeId: Byte, seqId: Int)

    @Throws(IOException::class)
    fun writeMessageEnd()

    @Throws(IOException::class)
    fun writeStructBegin(structName: String)

    @Throws(IOException::class)
    fun writeStructEnd()

    @Throws(IOException::class)
    fun writeFieldBegin(fieldName: String, fieldId: Int, typeId: Byte)

    @Throws(IOException::class)
    fun writeFieldEnd()

    @Throws(IOException::class)
    fun writeFieldStop()

    @Throws(IOException::class)
    fun writeMapBegin(keyTypeId: Byte, valueTypeId: Byte, mapSize: Int)

    @Throws(IOException::class)
    fun writeMapEnd()

    @Throws(IOException::class)
    fun writeListBegin(elementTypeId: Byte, listSize: Int)

    @Throws(IOException::class)
    fun writeListEnd()

    @Throws(IOException::class)
    fun writeSetBegin(elementTypeId: Byte, setSize: Int)

    @Throws(IOException::class)
    fun writeSetEnd()

    @Throws(IOException::class)
    fun writeBool(b: Boolean)

    @Throws(IOException::class)
    fun writeByte(b: Byte)

    @Throws(IOException::class)
    fun writeI16(i16: Short)

    @Throws(IOException::class)
    fun writeI32(i32: Int)

    @Throws(IOException::class)
    fun writeI64(i64: Long)

    @Throws(IOException::class)
    fun writeDouble(dub: Double)

    @Throws(IOException::class)
    fun writeString(str: String)

    @Throws(IOException::class)
    fun writeBinary(buf: ByteString)

    ////////

    @Throws(IOException::class)
    fun readMessageBegin(): MessageMetadata

    @Throws(IOException::class)
    fun readMessageEnd()

    @Throws(IOException::class)
    fun readStructBegin(): StructMetadata

    @Throws(IOException::class)
    fun readStructEnd()

    @Throws(IOException::class)
    fun readFieldBegin(): FieldMetadata

    @Throws(IOException::class)
    fun readFieldEnd()

    @Throws(IOException::class)
    fun readMapBegin(): MapMetadata

    @Throws(IOException::class)
    fun readMapEnd()

    @Throws(IOException::class)
    fun readListBegin(): ListMetadata

    @Throws(IOException::class)
    fun readListEnd()

    @Throws(IOException::class)
    fun readSetBegin(): SetMetadata

    @Throws(IOException::class)
    fun readSetEnd()

    @Throws(IOException::class)
    fun readBool(): Boolean

    @Throws(IOException::class)
    fun readByte(): Byte

    @Throws(IOException::class)
    fun readI16(): Short

    @Throws(IOException::class)
    fun readI32(): Int

    @Throws(IOException::class)
    fun readI64(): Long

    @Throws(IOException::class)
    fun readDouble(): Double

    @Throws(IOException::class)
    fun readString(): String

    @Throws(IOException::class)
    fun readBinary(): ByteString

    //////////////

    @Throws(IOException::class)
    fun skipBool()

    @Throws(IOException::class)
    fun skipByte()

    @Throws(IOException::class)
    fun skipI16()

    @Throws(IOException::class)
    fun skipI32()

    @Throws(IOException::class)
    fun skipI64()

    @Throws(IOException::class)
    fun skipDouble()

    @Throws(IOException::class)
    fun skipString()

    @Throws(IOException::class)
    fun skipStruct() {
        readStructBegin()
        while (true) {
            val fieldMetadata = readFieldBegin()
            if (fieldMetadata.typeId == TType.STOP) {
                break
            }
            skip(fieldMetadata.typeId)
            readFieldEnd()
        }
        readStructEnd()
    }

    @Throws(IOException::class)
    fun skipList() {
        val listMetadata = readListBegin()
        for (i in 0..<listMetadata.size) {
            skip(listMetadata.elementTypeId)
        }
        readListEnd()
    }

    @Throws(IOException::class)
    fun skipSet() {
        val setMetadata = readSetBegin()
        for (i in 0..<setMetadata.size) {
            skip(setMetadata.elementTypeId)
        }
        readSetEnd()
    }

    @Throws(IOException::class)
    fun skipMap() {
        val mapMetadata = readMapBegin()
        for (i in 0..<mapMetadata.size) {
            skip(mapMetadata.keyTypeId)
            skip(mapMetadata.valueTypeId)
        }
        readMapEnd()
    }

    @Throws(IOException::class)
    fun skip(typeCode: Byte) {
        when (typeCode) {
            TType.BOOL -> skipBool()
            TType.BYTE -> skipByte()
            TType.I16 -> skipI16()
            TType.I32 -> skipI32()
            TType.I64 -> skipI64()
            TType.DOUBLE -> skipDouble()
            TType.STRING -> skipString()
            TType.STRUCT -> skipStruct()
            TType.LIST -> skipList()
            TType.SET -> skipSet()
            TType.MAP -> skipMap()
            else -> throw ProtocolException("Unrecognized TType value: $typeCode")
        }
    }

    //////////////

    @Throws(IOException::class)
    fun flush()

    fun reset() {
        // to be implemented by implementations as needed
    }

    @Throws(IOException::class)
    override fun close()
}
