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

import jp.co.gahojin.thrifty.Adapter
import jp.co.gahojin.thrifty.Struct
import jp.co.gahojin.thrifty.StructBuilder
import jp.co.gahojin.thrifty.TType
import jp.co.gahojin.thrifty.ThriftField
import okio.IOException
import kotlin.jvm.JvmField

class Xtruct private constructor(builder: Builder) : Struct {
    @ThriftField(fieldId = 1)
    val stringThing: String? = builder.stringThing

    @ThriftField(fieldId = 4)
    val byteThing: Byte? = builder.byteThing

    @ThriftField(fieldId = 9)
    val i32Thing: Int? = builder.i32Thing

    @ThriftField(fieldId = 11)
    val i64Thing: Long? = builder.i64Thing

    @ThriftField(fieldId = 13)
    val doubleThing: Double? = builder.doubleThing

    @ThriftField(fieldId = 15)
    val boolThing: Boolean? = builder.boolThing
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (other !is Xtruct) return false
        return this@Xtruct.stringThing == other.stringThing
                && byteThing == other.byteThing
                && i32Thing == other.i32Thing
                && i64Thing == other.i64Thing
                && doubleThing == other.doubleThing
                && boolThing == other.boolThing
    }

    override fun hashCode(): Int {
        var code = 16777619
        code = code xor (this@Xtruct.stringThing?.hashCode() ?: 0)
        code *= -0x7ee3623b
        code = code xor (byteThing?.hashCode() ?: 0)
        code *= -0x7ee3623b
        code = code xor (i32Thing?.hashCode() ?: 0)
        code *= -0x7ee3623b
        code = code xor (i64Thing?.hashCode() ?: 0)
        code *= -0x7ee3623b
        code = code xor (doubleThing?.hashCode() ?: 0)
        code *= -0x7ee3623b
        code = code xor (boolThing?.hashCode() ?: 0)
        code *= -0x7ee3623b
        return code
    }

    override fun toString(): String {
        return "Xtruct{string_thing=${this@Xtruct.stringThing}, byte_thing=$byteThing, i32_thing=$i32Thing, i64_thing=$i64Thing, double_thing=$doubleThing, bool_thing=$boolThing}"
    }

    @Throws(IOException::class)
    override fun write(protocol: Protocol) {
        ADAPTER.write(protocol, this)
    }

    class Builder : StructBuilder<Xtruct> {
        var stringThing: String? = null
        var byteThing: Byte? = null
        var i32Thing: Int? = null
        var i64Thing: Long? = null
        var doubleThing: Double? = null
        var boolThing: Boolean? = null

        constructor(struct: Xtruct? = null) {
            stringThing = struct?.stringThing
            byteThing = struct?.byteThing
            i32Thing = struct?.i32Thing
            i64Thing = struct?.i64Thing
            doubleThing = struct?.doubleThing
            boolThing = struct?.boolThing
        }

        fun string_thing(value: String?): Builder {
            stringThing = value
            return this
        }

        fun byte_thing(value: Byte?): Builder {
            byteThing = value
            return this
        }

        fun i32_thing(value: Int?): Builder {
            i32Thing = value
            return this
        }

        fun i64_thing(value: Long?): Builder {
            i64Thing = value
            return this
        }

        fun double_thing(value: Double?): Builder {
            doubleThing = value
            return this
        }

        fun bool_thing(value: Boolean?): Builder {
            boolThing = value
            return this
        }

        override fun build(): Xtruct {
            return Xtruct(this)
        }

        override fun reset() {
            stringThing = null
            byteThing = null
            i32Thing = null
            i64Thing = null
            doubleThing = null
            boolThing = null
        }
    }

    private class XtructAdapter : Adapter<Xtruct, Builder> {
        override fun write(protocol: Protocol, struct: Xtruct) {
            protocol.writeStructBegin("Xtruct")
            struct.stringThing?.also {
                protocol.writeFieldBegin("string_thing", 1, TType.STRING)
                protocol.writeString(it)
                protocol.writeFieldEnd()
            }
            struct.byteThing?.also {
                protocol.writeFieldBegin("byte_thing", 4, TType.BYTE)
                protocol.writeByte(it)
                protocol.writeFieldEnd()
            }
            struct.i32Thing?.also {
                protocol.writeFieldBegin("i32_thing", 9, TType.I32)
                protocol.writeI32(it)
                protocol.writeFieldEnd()
            }
            struct.i64Thing?.also {
                protocol.writeFieldBegin("i64_thing", 11, TType.I64)
                protocol.writeI64(it)
                protocol.writeFieldEnd()
            }
            struct.doubleThing?.also {
                protocol.writeFieldBegin("double_thing", 13, TType.DOUBLE)
                protocol.writeDouble(it)
                protocol.writeFieldEnd()
            }
            struct.boolThing?.also {
                protocol.writeFieldBegin("bool_thing", 15, TType.BOOL)
                protocol.writeBool(it)
                protocol.writeFieldEnd()
            }
            protocol.writeFieldStop()
            protocol.writeStructEnd()
        }

        override fun read(protocol: Protocol, builder: Builder): Xtruct {
            protocol.readStructBegin()
            while (true) {
                val field = protocol.readFieldBegin()
                if (field.typeId == TType.STOP) {
                    break
                }
                when (field.fieldId.toInt()) {
                    1 -> {
                        if (field.typeId == TType.STRING) {
                            val value = protocol.readString()
                            builder.string_thing(value)
                        } else {
                            protocol.skip(field.typeId)
                        }
                    }

                    4 -> {
                        if (field.typeId == TType.BYTE) {
                            val value = protocol.readByte()
                            builder.byte_thing(value)
                        } else {
                            protocol.skip(field.typeId)
                        }
                    }

                    9 -> {
                        if (field.typeId == TType.I32) {
                            val value = protocol.readI32()
                            builder.i32_thing(value)
                        } else {
                            protocol.skip(field.typeId)
                        }
                    }

                    11 -> {
                        if (field.typeId == TType.I64) {
                            val value = protocol.readI64()
                            builder.i64_thing(value)
                        } else {
                            protocol.skip(field.typeId)
                        }
                    }

                    13 -> {
                        if (field.typeId == TType.DOUBLE) {
                            val value = protocol.readDouble()
                            builder.double_thing(value)
                        } else {
                            protocol.skip(field.typeId)
                        }
                    }

                    15 -> {
                        if (field.typeId == TType.BOOL) {
                            val value = protocol.readBool()
                            builder.bool_thing(value)
                        } else {
                            protocol.skip(field.typeId)
                        }
                    }

                    else -> {
                        protocol.skip(field.typeId)
                    }
                }
                protocol.readFieldEnd()
            }
            protocol.readStructEnd()
            return builder.build()
        }

        @Throws(IOException::class)
        override fun read(protocol: Protocol): Xtruct {
            return read(protocol, Builder())
        }
    }

    companion object {
        @JvmField
        val ADAPTER: Adapter<Xtruct, Builder> = XtructAdapter()
    }
}
