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
package jp.co.gahojin.thrifty.schema

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jp.co.gahojin.thrifty.schema.parser.ConstElement
import jp.co.gahojin.thrifty.schema.parser.ConstValueElement
import jp.co.gahojin.thrifty.schema.parser.DoubleValueElement
import jp.co.gahojin.thrifty.schema.parser.EnumElement
import jp.co.gahojin.thrifty.schema.parser.EnumMemberElement
import jp.co.gahojin.thrifty.schema.parser.IdentifierValueElement
import jp.co.gahojin.thrifty.schema.parser.IntValueElement
import jp.co.gahojin.thrifty.schema.parser.ListValueElement
import jp.co.gahojin.thrifty.schema.parser.LiteralValueElement
import jp.co.gahojin.thrifty.schema.parser.ScalarTypeElement
import jp.co.gahojin.thrifty.schema.parser.TypeElement
import jp.co.gahojin.thrifty.schema.parser.TypedefElement
import org.junit.jupiter.api.Test

class ConstantTest {
    private val symbolTable = TestSymbolTable()
    private val loc = Location.get("", "")

    private class TestSymbolTable : SymbolTable {
        private val symbols = mutableMapOf<String, Constant>()

        operator fun get(name: String): Constant? {
            return symbols[name]
        }

        operator fun set(name: String, constant: Constant) {
            symbols[name] = constant
        }

        override fun lookupConst(symbol: String): Constant? {
            return get(symbol)
        }
    }

    @Test
    fun boolLiteral() {
        Constant.validate(symbolTable, IdentifierValueElement(loc, "true", "true"), BuiltinType.BOOL)
        Constant.validate(symbolTable, IdentifierValueElement(loc, "false", "false"), BuiltinType.BOOL)
        val e = shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, LiteralValueElement(loc, "nope", "nope"), BuiltinType.BOOL)
        }

        e.message shouldContain "Expected 'true', 'false', '1', '0', or a bool constant"
    }

    @Test
    fun boolConstant() {
        val c = makeConstant(
            "aBool",
            ScalarTypeElement(loc, "string", null),
            IdentifierValueElement(loc, "aBool", "aBool"),
            BuiltinType.BOOL,
        )

        symbolTable["aBool"] = c

        Constant.validate(symbolTable, IdentifierValueElement(loc, "aBool", "aBool"), BuiltinType.BOOL)
    }

    @Test
    fun boolWithWrongTypeOfConstant() {
        val c = makeConstant(
            "aBool",
            ScalarTypeElement(loc, "string", null),
            IdentifierValueElement(loc, "aBool", "abool"),
            BuiltinType.STRING,
        )

        symbolTable["aBool"] = c

        shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, IdentifierValueElement(loc, "aBool", "aBool"), BuiltinType.BOOL)
        }
    }

    @Test
    fun boolWithNonConstantIdentifier() {
        val e = shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, IdentifierValueElement(loc, "someStruct", "someStruct"), BuiltinType.BOOL)
        }

        e.message shouldContain "Expected 'true', 'false', '1', '0', or a bool constant; got: someStruct"
    }

    @Test
    fun boolWithConstantHavingBoolTypedefValue() {
        val td = makeTypedef(BuiltinType.BOOL, "Truthiness")

        val c = makeConstant(
            "aBool",
            ScalarTypeElement(loc, "Truthiness", null),
            IdentifierValueElement(loc, "aBool", "aBool"),
            td,
        )

        symbolTable["aBool"] = c

        Constant.validate(symbolTable, IdentifierValueElement(loc, "aBool", "aBool"), BuiltinType.BOOL)
    }

    @Test
    fun typedefWithCorrectLiteral() {
        val td = makeTypedef(BuiltinType.STRING, "Message")

        val value = LiteralValueElement(loc, "\"y helo thar\"", "y helo thar")

        Constant.validate(symbolTable, value, td)
    }

    @Test
    fun inRangeInt() {
        val value = IntValueElement(loc, "10", 10)
        val type = BuiltinType.I32

        Constant.validate(symbolTable, value, type)
    }

    @Test
    fun tooLargeInt() {
        val value = IntValueElement(loc, "${Integer.MAX_VALUE.toLong() + 1}", Integer.MAX_VALUE.toLong() + 1)
        val type = BuiltinType.I32

        shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, value, type)
        }.message shouldContain "out of range for type i32"
    }

    @Test
    fun tooSmallInt() {
        val value = IntValueElement(loc, "${Integer.MIN_VALUE.toLong() - 1}", Integer.MIN_VALUE.toLong() - 1)
        val type = BuiltinType.I32

        shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, value, type)
        }.message shouldContain "out of range for type i32"
    }

    @Test
    fun doubleLiteral() {
        Constant.validate(symbolTable, IntValueElement(loc, "10", 10), BuiltinType.DOUBLE)
        Constant.validate(symbolTable, DoubleValueElement(loc, "3.14", 3.14), BuiltinType.DOUBLE)
        val e = shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, LiteralValueElement(loc, "aString", "aString"), BuiltinType.DOUBLE)
        }

        e.message shouldContain "Expected a value of type DOUBLE but got aString"
    }

    @Test
    fun doubleConstant() {
        val c = makeConstant(
            "aDouble",
            ScalarTypeElement(loc, "string", null),
            IdentifierValueElement(loc, "aDouble", "aDouble"),
            BuiltinType.DOUBLE,
        )

        symbolTable["aDouble"] = c

        Constant.validate(symbolTable, IdentifierValueElement(loc, "aDouble", "aDouble"), BuiltinType.DOUBLE)
    }

    @Test
    fun doubleWithWrongTypeOfConstant() {
        val c = makeConstant(
            "aString",
            ScalarTypeElement(loc, "string", null),
            IdentifierValueElement(loc, "aString", "aString"),
            BuiltinType.STRING,
        )

        symbolTable["aString"] = c

        val e = shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, IdentifierValueElement(loc, "aString", "aString"), BuiltinType.DOUBLE)
        }
        e.message shouldContain "Expected a value of type double, but got string"
    }

    @Test
    fun doubleWithNonConstantIdentifier() {
        val e = shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, IdentifierValueElement(loc, "someStruct", "someStruct"), BuiltinType.DOUBLE)
        }
        e.message shouldContain "Unrecognized const identifier: someStruct"
    }

    @Test
    fun enumWithMember() {
        val memberElements = listOf(EnumMemberElement(loc, "TEST", 1))

        val enumElement = EnumElement(loc, "TestEnum", memberElements)

        val et = EnumType(enumElement, emptyMap())

        Constant.validate(symbolTable, IdentifierValueElement(loc, "TestEnum.TEST", "TestEnum.TEST"), et)
    }

    @Test
    fun enumWithNonMemberIdentifier() {
        val memberElements = listOf(EnumMemberElement(loc, "TEST", 1))

        val enumElement = EnumElement(loc, "TestEnum", memberElements)

        val et = EnumType(enumElement, emptyMap())

        val e = shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable,
                IdentifierValueElement(loc, "TestEnum.NON_MEMBER", "TestEnum.NON_MEMBER"), et)
        }
        e.message shouldContain "'TestEnum.NON_MEMBER' is not a member of enum type TestEnum: members=[TEST]"
    }

    @Test
    fun unqualifiedEnumMember() {
        val memberElements = listOf(EnumMemberElement(loc, "TEST", 1))

        val enumElement = EnumElement(loc, "TestEnum", memberElements)

        val et = EnumType(enumElement, emptyMap())

        shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, IdentifierValueElement(loc, "TEST", "TEST"), et)
        }.message shouldContain "Unqualified name 'TEST' is not a valid enum constant value"
    }

    @Test
    fun listOfInts() {
        val list = ListType(BuiltinType.I32)
        val listValue = ListValueElement(location = loc, thriftText = "[0, 1, 2]", value = listOf(
            IntValueElement(loc, "0", 0),
            IntValueElement(loc, "1", 1),
            IntValueElement(loc, "2", 2),
        ))

        Constant.validate(symbolTable, listValue, list)
    }

    @Test
    fun heterogeneousList() {
        val list = ListType(BuiltinType.I32)
        val listValue = ListValueElement(location = loc, thriftText = "[0, 1, \"2\"]", value = listOf(
            IntValueElement(loc, "0", 0),
            IntValueElement(loc, "1", 1),
            LiteralValueElement(loc, "\"2\"", "2"),
        ))

        shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, listValue, list)
        }.message shouldContain "Expected a value of type i32 but got 2"
    }

    @Test
    fun typedefOfEnum() {
        val memberElements = listOf(EnumMemberElement(loc, "FOO", 1))

        val enumElement = EnumElement(loc, "AnEnum", memberElements)

        val et = EnumType(enumElement, emptyMap())

        val typedefType = makeTypedef(et, "Id")

        val value = IdentifierValueElement(loc, "AnEnum.FOO", "AnEnum.FOO")

        Constant.validate(symbolTable, value, typedefType)
    }

    @Test
    fun typedefOfWrongEnum() {
        val wrongMemberElements = listOf(EnumMemberElement(loc, "BAR", 2))

        val wrongEnumElement = EnumElement(loc, "DifferentEnum", wrongMemberElements)

        val wt = EnumType(wrongEnumElement, emptyMap())

        val typedefType = makeTypedef(wt, "Id")

        val value = IdentifierValueElement(loc, "AnEnum.FOO", "AnEnum.FOO")

        shouldThrow<IllegalStateException> {
            Constant.validate(symbolTable, value, typedefType)
        }.message shouldBe "'AnEnum.FOO' is not a member of enum type DifferentEnum: members=[BAR]"
    }

    @Test
    fun setOfInts() {
        val setType = SetType(BuiltinType.I32)
        val listValue = ListValueElement(location = loc, thriftText = "[0, 1, 2]", value = listOf(
            IntValueElement(loc, "0", 0),
            IntValueElement(loc, "1", 1),
            IntValueElement(loc, "2", 2),
        ))

        Constant.validate(symbolTable, listValue, setType)
    }

    private fun makeConstant(
        name: String,
        typeElement: TypeElement,
        value: ConstValueElement,
        thriftType: ThriftType,
    ): Constant {
        val element = ConstElement(loc, typeElement, name, value)

        return Constant(element, emptyMap(), thriftType)
    }

    private fun makeTypedef(oldType: ThriftType, newName: String): TypedefType {
        val element = TypedefElement(
            loc,
            ScalarTypeElement(loc, "does_not_matter", null),
            newName,
        )

        return TypedefType(element, emptyMap(), oldType)
    }
}
