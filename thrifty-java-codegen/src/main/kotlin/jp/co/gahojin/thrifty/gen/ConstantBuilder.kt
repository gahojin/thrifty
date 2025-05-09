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
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.NameAllocator
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import jp.co.gahojin.thrifty.schema.BuiltinType
import jp.co.gahojin.thrifty.schema.EnumType
import jp.co.gahojin.thrifty.schema.ListType
import jp.co.gahojin.thrifty.schema.MapType
import jp.co.gahojin.thrifty.schema.NamespaceScope
import jp.co.gahojin.thrifty.schema.Schema
import jp.co.gahojin.thrifty.schema.ServiceType
import jp.co.gahojin.thrifty.schema.SetType
import jp.co.gahojin.thrifty.schema.StructType
import jp.co.gahojin.thrifty.schema.ThriftType
import jp.co.gahojin.thrifty.schema.TypedefType
import jp.co.gahojin.thrifty.schema.parser.ConstValueElement
import jp.co.gahojin.thrifty.schema.parser.DoubleValueElement
import jp.co.gahojin.thrifty.schema.parser.IdentifierValueElement
import jp.co.gahojin.thrifty.schema.parser.IntValueElement
import jp.co.gahojin.thrifty.schema.parser.ListValueElement
import jp.co.gahojin.thrifty.schema.parser.LiteralValueElement
import jp.co.gahojin.thrifty.schema.parser.MapValueElement
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class ConstantBuilder(
    private val typeResolver: TypeResolver,
    private val fieldNamer: FieldNamer,
    private val schema: Schema,
) {
    fun generateFieldInitializer(
        initializer: CodeBlock.Builder,
        allocator: NameAllocator,
        scope: AtomicInteger,
        name: String,
        tt: ThriftType,
        value: ConstValueElement,
        needsDeclaration: Boolean,
    ) {
        tt.trueType.accept(object : SimpleVisitor<Unit>() {
            override fun visitBuiltin(builtinType: ThriftType) {
                val init = renderConstValue(initializer, allocator, scope, tt, value)
                initializer.addStatement("\$L = \$L", name, init)
            }

            override fun visitEnum(enumType: EnumType) {
                val item = renderConstValue(initializer, allocator, scope, tt, value)

                initializer.addStatement("\$L = \$L", name, item)
            }

            override fun visitList(listType: ListType) {
                val list = (value as ListValueElement).value
                val elementType = listType.elementType.trueType
                val elementTypeName = typeResolver.getJavaClass(elementType)
                val genericName = ParameterizedTypeName.get(TypeNames.LIST, elementTypeName)
                val listImplName = typeResolver.listOf(elementTypeName)
                generateSingleElementCollection(elementType, genericName, listImplName, list)
            }

            override fun visitSet(setType: SetType) {
                val set = (value as ListValueElement).value
                val elementType = setType.elementType.trueType
                val elementTypeName = typeResolver.getJavaClass(elementType)
                val genericName = ParameterizedTypeName.get(TypeNames.SET, elementTypeName)
                val setImplName = typeResolver.setOf(elementTypeName)
                generateSingleElementCollection(elementType, genericName, setImplName, set)
            }

            private fun generateSingleElementCollection(
                elementType: ThriftType,
                genericName: TypeName,
                collectionImplName: TypeName,
                values: List<ConstValueElement>,
            ) {
                if (needsDeclaration) {
                    initializer.addStatement("\$T \$N = new \$T()", genericName, name, collectionImplName)
                } else {
                    initializer.addStatement("\$N = new \$T()", name, collectionImplName)
                }

                for (element in values) {
                    val elementName = renderConstValue(initializer, allocator, scope, elementType, element)
                    initializer.addStatement("\$N.add(\$L)", name, elementName)
                }
            }

            override fun visitMap(mapType: MapType) {
                val map = (value as MapValueElement).value
                val keyType = mapType.keyType.trueType
                val valueType = mapType.valueType.trueType

                val keyTypeName = typeResolver.getJavaClass(keyType)
                val valueTypeName = typeResolver.getJavaClass(valueType)
                val mapImplName = typeResolver.mapOf(keyTypeName, valueTypeName)

                if (needsDeclaration) {
                    initializer.addStatement(
                        "\$T \$N = new \$T()",
                        ParameterizedTypeName.get(TypeNames.MAP, keyTypeName, valueTypeName),
                        name,
                        mapImplName,
                    )
                } else {
                    initializer.addStatement("\$N = new \$T()", name, mapImplName)
                }

                for ((key, value1) in map) {
                    val keyName = renderConstValue(initializer, allocator, scope, keyType, key)
                    val valueName = renderConstValue(initializer, allocator, scope, valueType, value1)
                    initializer.addStatement("\$N.put(\$L, \$L)", name, keyName, valueName)
                }
            }

            override fun visitStruct(structType: StructType) {
                val structTypeName = typeResolver.getJavaClass(structType) as ClassName
                val builderTypeName = structTypeName.nestedClass("Builder")
                val loweredStructName = structType.name.replaceFirstChar { it.lowercase(Locale.US) }
                val builderName = "${loweredStructName}Builder${scope.getAndIncrement()}"

                initializer.addStatement("\$1T \$2N = new \$1T()", builderTypeName, builderName)

                val fieldsByName = structType.fields.associateBy { it.name }

                val map = (value as MapValueElement).value
                for ((keyElement, valueElement) in map) {
                    val key = (keyElement as LiteralValueElement).value
                    val field = checkNotNull(fieldsByName[key]) {
                        "Struct ${structType.name} has no field named '$key'"
                    }
                    val setterName = fieldNamer.getName(field)
                    val valueName = renderConstValue(initializer, allocator, scope, field.type, valueElement)
                    initializer.addStatement("\$N.\$N(\$L)", builderName, setterName, valueName)
                }

                if (needsDeclaration) {
                    initializer.addStatement("\$T \$N = \$N.build()", structTypeName, name, builderName)
                } else {
                    initializer.addStatement("\$N = \$N.build()", name, builderName)
                }
            }

            override fun visitTypedef(typedefType: TypedefType) {
                throw AssertionError("Should not be possible!")
            }

            override fun visitService(serviceType: ServiceType) {
                throw AssertionError("Should not be possible!")
            }

            override fun visitVoid(voidType: BuiltinType) {
                throw AssertionError("Should not be possible!")
            }
        })
    }

    fun renderConstValue(
        block: CodeBlock.Builder,
        allocator: NameAllocator,
        scope: AtomicInteger,
        type: ThriftType,
        value: ConstValueElement,
    ): CodeBlock {
        return type.accept(ConstRenderingVisitor(block, allocator, scope, type, value))
    }

    private inner class ConstRenderingVisitor(
        val block: CodeBlock.Builder,
        val allocator: NameAllocator,
        val scope: AtomicInteger,
        val type: ThriftType,
        val value: ConstValueElement,
    ) : ThriftType.Visitor<CodeBlock> {
        private fun getNumberLiteral(element: ConstValueElement): Any {
            if (element !is IntValueElement) {
                throw AssertionError("Expected an int or double, got: $element")
            }

            return if (element.thriftText.startsWith("0x") || element.thriftText.startsWith("0X")) {
                element.thriftText
            } else {
                element.value
            }
        }

        override fun visitBool(boolType: BuiltinType): CodeBlock {
            val name = if (value is IdentifierValueElement && value.value in setOf("true", "false")) {
                value.value
            } else if (value is IntValueElement) {
                if (value.value == 0L) "false" else "true"
            } else {
                return constantOrError("Invalid boolean constant")
            }

            return CodeBlock.of(name)
        }

        override fun visitByte(byteType: BuiltinType): CodeBlock {
            return if (value is IntValueElement) {
                CodeBlock.of("(byte) \$L", getNumberLiteral(value))
            } else {
                constantOrError("Invalid byte constant")
            }
        }

        override fun visitI16(i16Type: BuiltinType): CodeBlock {
            return if (value is IntValueElement) {
                CodeBlock.of("(short) \$L", getNumberLiteral(value))
            } else {
                constantOrError("Invalid i16 constant")
            }
        }

        override fun visitI32(i32Type: BuiltinType): CodeBlock {
            return if (value is IntValueElement) {
                CodeBlock.of("\$L", getNumberLiteral(value))
            } else {
                constantOrError("Invalid i32 constant")
            }
        }

        override fun visitI64(i64Type: BuiltinType): CodeBlock {
            return if (value is IntValueElement) {
                CodeBlock.of("\$LL", getNumberLiteral(value))
            } else {
                constantOrError("Invalid i64 constant")
            }
        }

        override fun visitDouble(doubleType: BuiltinType): CodeBlock {
            return when (value) {
                is IntValueElement -> CodeBlock.of("(double) \$L", value.value)
                is DoubleValueElement -> CodeBlock.of("\$L", value.value)
                else -> constantOrError("Invalid double constant")
            }
        }

        override fun visitString(stringType: BuiltinType): CodeBlock {
            return if (value is LiteralValueElement) {
                CodeBlock.of("\$S", value.value)
            } else {
                constantOrError("Invalid string constant")
            }
        }

        override fun visitBinary(binaryType: BuiltinType): CodeBlock {
            throw UnsupportedOperationException("Binary literals are not supported")
        }

        override fun visitVoid(voidType: BuiltinType): CodeBlock {
            throw AssertionError("Void literals are meaningless, what are you even doing")
        }

        override fun visitEnum(enumType: EnumType): CodeBlock {
            val member = try {
                when (value) {
                    is IntValueElement -> enumType.findMemberById(value.value.toInt())
                    is IdentifierValueElement -> {
                        try {
                            return constantOrError("this is gross, sorry")
                        } catch (_: IllegalStateException) {
                            // Not a constant
                        }

                        // Remove the enum name prefix, assuming it is present
                        val name = value.value.split(".").last()
                        enumType.findMemberByName(name)
                    }

                    else -> throw AssertionError("Constant value $value is not possibly an enum; validation bug")
                }
            } catch (_: NoSuchElementException) {
                error("No enum member in ${enumType.name} with value $value")
            }

            return CodeBlock.of("\$T.\$L", typeResolver.getJavaClass(enumType), member.name)
        }

        override fun visitList(listType: ListType): CodeBlock {
            return if (value is ListValueElement) {
                if (value.value.isEmpty()) {
                    val elementType = typeResolver.getJavaClass(listType.elementType)
                    CodeBlock.of("\$T.<\$T>emptyList()", TypeNames.COLLECTIONS, elementType)
                } else {
                    visitCollection(listType, "list", "unmodifiableList")
                }
            } else {
                constantOrError("Invalid list constant")
            }
        }

        override fun visitSet(setType: SetType): CodeBlock {
            return if (value is ListValueElement) { // not a typo; ListValueElement covers lists and sets.
                if (value.value.isEmpty()) {
                    val elementType = typeResolver.getJavaClass(setType.elementType)
                    CodeBlock.of("\$T.<\$T>emptySet()", TypeNames.COLLECTIONS, elementType)
                } else {
                    visitCollection(setType, "set", "unmodifiableSet")
                }
            } else {
                constantOrError("Invalid set constant")
            }
        }

        override fun visitMap(mapType: MapType): CodeBlock {
            return if (value is MapValueElement) {
                if (value.value.isEmpty()) {
                    val keyType = typeResolver.getJavaClass(mapType.keyType)
                    val valueType = typeResolver.getJavaClass(mapType.valueType)
                    CodeBlock.of("\$T.<\$T, \$T>emptyMap()", TypeNames.COLLECTIONS, keyType, valueType)
                } else {
                    visitCollection(mapType, "map", "unmodifiableMap")
                }
            } else {
                constantOrError("Invalid map constant")
            }
        }

        private fun visitCollection(
            type: ThriftType,
            tempName: String,
            method: String,
        ): CodeBlock {
            val name = allocator.newName(tempName, scope.getAndIncrement())
            generateFieldInitializer(block, allocator, scope, name, type, value, true)
            return CodeBlock.of("\$T.\$L(\$N)", TypeNames.COLLECTIONS, method, name)
        }

        override fun visitStruct(structType: StructType): CodeBlock {
            val loweredStructName = structType.name.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val name = allocator.newName(loweredStructName, scope.getAndIncrement())
            generateFieldInitializer(block, allocator, scope, name, type, value, true)
            return CodeBlock.of(name)
        }

        override fun visitTypedef(typedefType: TypedefType): CodeBlock {
            return typedefType.oldType.accept(this)
        }

        override fun visitService(serviceType: ServiceType): CodeBlock {
            error("constants cannot be services")
        }

        private fun constantOrError(error: String): CodeBlock {
            val message = "$error: $value + at ${value.location}"

            if (value !is IdentifierValueElement) {
                error(message)
            }

            val expectedType = type.trueType

            var name = value.value
            val ix = name.indexOf('.')
            var expectedProgram: String? = null
            if (ix != -1) {
                expectedProgram = name.substring(0, ix)
                name = name.substring(ix + 1)
            }

            // TODO(ben): Think of a more systematic way to know what [Program] owns a thrift element
            val c = schema.constants
                .asSequence()
                .filter { it.name == name }
                .filter { it.type.trueType == expectedType }
                .filter { expectedProgram == null || it.location.programName == expectedProgram }
                .firstOrNull() ?: error(message)

            val packageName = c.getNamespaceFor(NamespaceScope.JAVA)
            return CodeBlock.of("$packageName.Constants.$name")
        }
    }
}
