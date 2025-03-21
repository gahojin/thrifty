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

import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import jp.co.gahojin.thrifty.Adapter
import jp.co.gahojin.thrifty.protocol.Protocol
import jp.co.gahojin.thrifty.schema.BuiltinType
import jp.co.gahojin.thrifty.schema.EnumType
import jp.co.gahojin.thrifty.schema.ListType
import jp.co.gahojin.thrifty.schema.MapType
import jp.co.gahojin.thrifty.schema.NamespaceScope
import jp.co.gahojin.thrifty.schema.ServiceType
import jp.co.gahojin.thrifty.schema.SetType
import jp.co.gahojin.thrifty.schema.StructType
import jp.co.gahojin.thrifty.schema.ThriftType
import jp.co.gahojin.thrifty.schema.TypedefType

/**
 * Generates Java code to write the value of a field in a [Adapter.write]
 * implementation.
 *
 * Handles nested values like lists, sets, maps, and user types.
 *
 * @param resolver the [TypeResolver] singleton
 * @param write the [Adapter.write] method under construction
 * @param proto the name of the [Protocol] parameter to the write method
 * @param subject the name of the struct parameter to the write method
 * @param fieldName the Java name of the field being written
 */
internal class GenerateWriterVisitor(
    private val resolver: TypeResolver,
    private val write: MethodSpec.Builder,
    private val proto: String,
    subject: String,
    fieldName: String,
) : ThriftType.Visitor<Unit> {
    /**
     * A stack of names, with the topmost name being the one currently
     * being written/assigned.
     */
    private val nameStack = ArrayDeque<String>().apply {
        addLast("$subject.$fieldName")
    }

    /**
     * A count of nested scopes.  Used to prevent name clashes for iterator
     * and temporary names used when writing nested collections.
     */
    private var scopeLevel: Int = 0

    override fun visitBool(boolType: BuiltinType) {
        write.addStatement("\$N.writeBool(\$L)", proto, nameStack.last())
    }

    override fun visitByte(byteType: BuiltinType) {
        write.addStatement("\$N.writeByte(\$L)", proto, nameStack.last())
    }

    override fun visitI16(i16Type: BuiltinType) {
        write.addStatement("\$N.writeI16(\$L)", proto, nameStack.last())
    }

    override fun visitI32(i32Type: BuiltinType) {
        write.addStatement("\$N.writeI32(\$L)", proto, nameStack.last())
    }

    override fun visitI64(i64Type: BuiltinType) {
        write.addStatement("\$N.writeI64(\$L)", proto, nameStack.last())
    }

    override fun visitDouble(doubleType: BuiltinType) {
        write.addStatement("\$N.writeDouble(\$L)", proto, nameStack.last())
    }

    override fun visitString(stringType: BuiltinType) {
        write.addStatement("\$N.writeString(\$L)", proto, nameStack.last())
    }

    override fun visitBinary(binaryType: BuiltinType) {
        write.addStatement("\$N.writeBinary(\$L)", proto, nameStack.last())
    }

    override fun visitVoid(voidType: BuiltinType) {
        throw AssertionError("Fields cannot be void")
    }

    override fun visitEnum(enumType: EnumType) {
        write.addStatement("\$N.writeI32(\$L.value)", proto, nameStack.last())
    }

    override fun visitList(listType: ListType) {
        visitSingleElementCollection(
            listType.elementType.trueType,
            "writeListBegin",
            "writeListEnd",
        )
    }

    override fun visitSet(setType: SetType) {
        visitSingleElementCollection(
            setType.elementType.trueType,
            "writeSetBegin",
            "writeSetEnd",
        )
    }

    private fun visitSingleElementCollection(elementType: ThriftType, beginMethod: String, endMethod: String) {
        val item = "item$scopeLevel"

        val javaClass = resolver.getJavaClass(elementType)
        val typeCode = resolver.getTypeCode(elementType)
        val typeCodeName = TypeNames.getTypeCodeName(typeCode)

        write.addStatement(
            "\$N.\$L(\$T.\$L, \$L.size())",
            proto,
            beginMethod,
            TypeNames.TTYPE,
            typeCodeName,
            nameStack.last(),
        )

        write.beginControlFlow("for (\$T \$N : \$L)", javaClass, item, nameStack.last())

        scope {
            nameStack.addLast(item)
            elementType.accept(this)
            nameStack.removeLast()
        }

        write.endControlFlow()

        write.addStatement("\$N.\$L()", proto, endMethod)
    }

    override fun visitMap(mapType: MapType) {
        val entryName = "entry$scopeLevel"
        val keyName = "key$scopeLevel"
        val valueName = "value$scopeLevel"
        val kt = mapType.keyType.trueType
        val vt = mapType.valueType.trueType

        val keyTypeCode = resolver.getTypeCode(kt)
        val valTypeCode = resolver.getTypeCode(vt)

        write.addStatement(
            "$1N.writeMapBegin($2T.$3L, $2T.$4L, $5L.size())",
            proto,
            TypeNames.TTYPE,
            TypeNames.getTypeCodeName(keyTypeCode),
            TypeNames.getTypeCodeName(valTypeCode),
            nameStack.last(),
        )

        val keyTypeName = resolver.getJavaClass(kt)
        val valueTypeName = resolver.getJavaClass(vt)
        val entry = ParameterizedTypeName.get(TypeNames.MAP_ENTRY, keyTypeName, valueTypeName)
        write.beginControlFlow("for (\$T \$N : \$L.entrySet())", entry, entryName, nameStack.last())
        write.addStatement("\$T \$N = \$N.getKey()", keyTypeName, keyName, entryName)
        write.addStatement("\$T \$N = \$N.getValue()", valueTypeName, valueName, entryName)

        scope {
            nameStack.addLast(keyName)
            kt.accept(this)
            nameStack.removeLast()

            nameStack.addLast(valueName)
            vt.accept(this)
            nameStack.removeLast()
        }

        write.endControlFlow()
        write.addStatement("\$N.writeMapEnd()", proto)
    }

    override fun visitStruct(structType: StructType) {
        val javaName = "${structType.getNamespaceFor(NamespaceScope.JAVA)}.${structType.name}"
        write.addStatement("\$L.ADAPTER.write(\$N, \$L)", javaName, proto, nameStack.last())
    }

    override fun visitTypedef(typedefType: TypedefType) {
        typedefType.trueType.accept(this)
    }

    override fun visitService(serviceType: ServiceType) {
        throw AssertionError("Cannot write a service")
    }

    private inline fun scope(fn: () -> Unit) {
        scopeLevel++
        try {
            fn()
        } finally {
            scopeLevel--
        }
    }
}
