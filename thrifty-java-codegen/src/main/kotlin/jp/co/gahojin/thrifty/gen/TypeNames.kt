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
package jp.co.gahojin.thrifty.gen

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import jp.co.gahojin.thrifty.Adapter
import jp.co.gahojin.thrifty.Obfuscated
import jp.co.gahojin.thrifty.Redacted
import jp.co.gahojin.thrifty.StructBuilder
import jp.co.gahojin.thrifty.TType
import jp.co.gahojin.thrifty.ThriftException
import jp.co.gahojin.thrifty.ThriftField
import jp.co.gahojin.thrifty.protocol.FieldMetadata
import jp.co.gahojin.thrifty.protocol.ListMetadata
import jp.co.gahojin.thrifty.protocol.MapMetadata
import jp.co.gahojin.thrifty.protocol.MessageMetadata
import jp.co.gahojin.thrifty.protocol.Protocol
import jp.co.gahojin.thrifty.protocol.SetMetadata
import jp.co.gahojin.thrifty.service.AsyncClientBase
import jp.co.gahojin.thrifty.service.MethodCall
import jp.co.gahojin.thrifty.service.ServiceMethodCallback
import jp.co.gahojin.thrifty.service.TMessageType
import jp.co.gahojin.thrifty.util.ObfuscationUtil
import jp.co.gahojin.thrifty.util.ProtocolUtil
import okio.ByteString
import java.io.IOException
import java.net.ProtocolException
import java.util.*

/**
 * JavaPoet type names used for code generation.
 */
internal object TypeNames {
    val BOOLEAN: TypeName = ClassName.BOOLEAN.box()
    val BYTE: TypeName = ClassName.BYTE.box()
    val SHORT: TypeName = ClassName.SHORT.box()
    val INTEGER: TypeName = ClassName.INT.box()
    val LONG: TypeName = ClassName.LONG.box()
    val DOUBLE: TypeName = ClassName.DOUBLE.box()
    val VOID: TypeName = ClassName.VOID // Don't box void, it is only used for methods returning nothing.

    val COLLECTIONS = classNameOf<Collections>()
    val STRING = classNameOf<String>()
    val LIST = classNameOf<List<*>>()
    val MAP = classNameOf<Map<*, *>>()
    val MAP_ENTRY = classNameOf<Map.Entry<*, *>>()
    val SET = classNameOf<Set<*>>()
    val BYTE_STRING = classNameOf<ByteString>()
    val STRING_BUILDER = classNameOf<StringBuilder>()
    val ILLEGAL_STATE_EXCEPTION = classNameOf<java.lang.IllegalStateException>()
    val ILLEGAL_ARGUMENT_EXCEPTION = classNameOf<java.lang.IllegalArgumentException>()
    val NULL_POINTER_EXCEPTION = classNameOf<java.lang.NullPointerException>()

    val ARRAY_LIST = classNameOf<ArrayList<*>>()
    val LINKED_HASH_MAP = classNameOf<LinkedHashMap<*, *>>()
    val LINKED_HASH_SET = classNameOf<LinkedHashSet<*>>()

    val LIST_META = classNameOf<ListMetadata>()
    val SET_META = classNameOf<SetMetadata>()
    val MAP_META = classNameOf<MapMetadata>()

    val PROTOCOL = classNameOf<Protocol>()
    val PROTO_UTIL = classNameOf<ProtocolUtil>()
    val PROTOCOL_EXCEPTION = classNameOf<ProtocolException>()
    val IO_EXCEPTION = classNameOf<IOException>()
    val EXCEPTION = classNameOf<java.lang.Exception>()
    val TTYPE = classNameOf<TType>()
    val TMESSAGE_TYPE = classNameOf<TMessageType>()

    val THRIFT_EXCEPTION = classNameOf<ThriftException>()
    val THRIFT_EXCEPTION_KIND = classNameOf<ThriftException.Kind>()

    val BUILDER = classNameOf<StructBuilder<*>>()
    val ADAPTER = classNameOf<Adapter<*, *>>()

    val FIELD_METADATA = classNameOf<FieldMetadata>()
    val MESSAGE_METADATA = classNameOf<MessageMetadata>()

    val OVERRIDE = classNameOf<Override>()
    val DEPRECATED = classNameOf<java.lang.Deprecated>()
    val SUPPRESS_WARNINGS = classNameOf<java.lang.SuppressWarnings>()
    val REDACTED = classNameOf<Redacted>()
    val OBFUSCATED = classNameOf<Obfuscated>()
    val THRIFT_FIELD = classNameOf<ThriftField>()

    val ANDROID_SUPPORT_NOT_NULL = ClassName.get("android.support.annotation", "NonNull")
    val ANDROID_SUPPORT_NULLABLE = ClassName.get("android.support.annotation", "Nullable")
    val ANDROIDX_NOT_NULL = ClassName.get("androidx.annotation", "NonNull")
    val ANDROIDX_NULLABLE = ClassName.get("androidx.annotation", "Nullable")

    val SERVICE_CALLBACK = classNameOf<ServiceMethodCallback<*>>()
    val SERVICE_CLIENT_BASE = classNameOf<AsyncClientBase>()
    val SERVICE_CLIENT_LISTENER = classNameOf<AsyncClientBase.Listener>()
    val SERVICE_METHOD_CALL = classNameOf<MethodCall<*>>()

    val PARCEL = ClassName.get("android.os", "Parcel")
    val PARCELABLE = ClassName.get("android.os", "Parcelable")
    val PARCELABLE_CREATOR = ClassName.get("android.os", "Parcelable", "Creator")

    val OBFUSCATION_UTIL = classNameOf<ObfuscationUtil>()

    /**
     * Gets the [TType] member name corresponding to the given type-code.
     *
     * @param code the code whose name is needed
     * @return the TType member name as a string
     */
    fun getTypeCodeName(code: Byte): String {
        return when(code) {
            TType.BOOL -> "BOOL"
            TType.BYTE -> "BYTE"
            TType.I16 -> "I16"
            TType.I32 -> "I32"
            TType.I64 -> "I64"
            TType.DOUBLE -> "DOUBLE"
            TType.STRING -> "STRING"
            TType.STRUCT -> "STRUCT"
            TType.LIST -> "LIST"
            TType.SET -> "SET"
            TType.MAP -> "MAP"
            TType.VOID -> "VOID"
            TType.STOP -> "STOP"
            else -> throw NoSuchElementException("not a TType member: $code")
        }
    }
}

enum class NullabilityAnnotationType(
    internal val notNullClassName: ClassName?,
    internal val nullableClassName: ClassName?
) {
    NONE(null, null),
    ANDROID_SUPPORT(
        notNullClassName = TypeNames.ANDROID_SUPPORT_NOT_NULL,
        nullableClassName = TypeNames.ANDROID_SUPPORT_NULLABLE,
    ),
    ANDROIDX(
        notNullClassName = TypeNames.ANDROIDX_NOT_NULL,
        nullableClassName = TypeNames.ANDROIDX_NULLABLE,
    ),
}

internal inline fun <reified T> classNameOf(): ClassName = ClassName.get(T::class.java)
