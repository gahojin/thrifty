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
package jp.co.gahojin.thrifty.kgen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.contain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import jp.co.gahojin.thrifty.schema.FieldNamingPolicy
import jp.co.gahojin.thrifty.schema.Loader
import jp.co.gahojin.thrifty.schema.Schema
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.File

@Execution(ExecutionMode.CONCURRENT)
class KotlinCodeGeneratorTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `struct to data class`() {
        val schema = load("""
            namespace kt com.test

            // This is an enum
            enum MyEnum {
              MEMBER_ONE, // trailing doc
              MEMBER_TWO

              // leading doc
              MEMBER_THREE = 4
            }

            const i32 FooNum = 42

            const string ConstStr = "wtf"

            const list<string> ConstStringList = ["wtf", "mate"]
            const map<string, list<string>> Weird = { "foo": ["a", "s", "d", "f"],
                                                      "bar": ["q", "w", "e", "r"] }
            //const binary ConstBin = "DEADBEEF"

            struct Test {
              1: required string Foo (thrifty.redacted = "1");
              2: required map<i64, string> Numbers (thrifty.obfuscated = "1");
              3: optional string Bar;
              5: optional set<list<double>> Bs = [[1.0], [2.0], [3.0], [4.0]];
              6: MyEnum enumType;
              7: set<i8> Bytes;
              8: list<list<string>> listOfStrings
            }

            struct AnotherOne {
              1: optional i32 NumBitTheDust = 900
            }
        """.trimIndent())

        val files = KotlinCodeGenerator(FieldNamingPolicy.JAVA).generate(schema)

        files.shouldCompile()
    }

    @Test
    fun `output styles work as advertised`() {
        val thrift = """
            namespace kt com.test

            enum AnEnum {
              ONE,
              TWO
            }

            struct AStruct {
              1: optional string ssn;
            }
        """.trimIndent()

        val schema = load(thrift)
        val gen = KotlinCodeGenerator()

        // Default should be one file per namespace
        gen.outputStyle shouldBe KotlinCodeGenerator.OutputStyle.FILE_PER_NAMESPACE
        gen.generate(schema).size shouldBe 1

        gen.outputStyle = KotlinCodeGenerator.OutputStyle.FILE_PER_TYPE
        gen.generate(schema).size shouldBe 2
    }

    @Test
    fun `file-per-type puts constants into a file named 'Constants'`() {
        val thrift = """
            namespace kt com.test

            const i32 ONE = 1;
            const i64 TWO = 2;
            const string THREE = "three";
        """.trimIndent()

        val schema = load(thrift)
        val gen = KotlinCodeGenerator().filePerType()
        val specs = gen.generate(schema)

        specs.shouldCompile()
        specs.single().name shouldBe "Constants" // ".kt" suffix is appended when the file is written out
    }

    @Test
    fun `empty structs get default equals, hashcode, and toString methods`() {
        val thrift = """
            namespace kt com.test

            struct Empty {}
        """.trimIndent()

        val specs = generate(thrift)
        specs.shouldCompile()

        val struct = specs.single().members.single() as TypeSpec

        struct.name shouldBe "Empty"
        struct.modifiers.any { it == KModifier.DATA } shouldBe false
        struct.funSpecs.any { it.name == "toString" } shouldBe true
        struct.funSpecs.any { it.name == "hashCode" } shouldBe true
        struct.funSpecs.any { it.name == "equals" } shouldBe true
    }

    @Test
    fun `Non-empty structs are data classes`() {
        val thrift = """
            namespace kt com.test

            struct NonEmpty {
              1: required i32 Number
              2: required string Text;
              3: required set<i8> Bytes;
              4: required list<list<string>> listOfStrings
              5: required NonEmpty child;
            }
        """.trimIndent()

        val specs = generate(thrift)
        specs.shouldCompile()

        val struct = specs.single().members.single() as TypeSpec

        struct.name shouldBe "NonEmpty"
        struct.modifiers.any { it == KModifier.DATA } shouldBe true
        struct.funSpecs.any { it.name == "toString" } shouldBe false
        struct.funSpecs.any { it.name == "hashCode" } shouldBe false
        struct.funSpecs.any { it.name == "equals" } shouldBe false

        struct.toString() shouldContain  """
            |public data class NonEmpty(
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 1,
            |    isRequired = true,
            |  )
            |  public val Number: kotlin.Int = 0,
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 2,
            |    isRequired = true,
            |  )
            |  public val Text: kotlin.String = "",
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 3,
            |    isRequired = true,
            |  )
            |  public val Bytes: kotlin.collections.Set<kotlin.Byte> = kotlin.collections.emptySet(),
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 4,
            |    isRequired = true,
            |  )
            |  public val listOfStrings: kotlin.collections.List<kotlin.collections.List<kotlin.String>> = kotlin.collections.emptyList(),
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 5,
            |    isRequired = true,
            |  )
            |  public val child: com.test.NonEmpty,
            |) : jp.co.gahojin.thrifty.Struct {
            """.trimMargin()
    }

    @Test
    fun `Non-empty structs are data classes with jvmOverloads`() {
        val thrift = """
            namespace kt com.test

            struct NonEmpty {
              1: required i32 Number
              2: required string Text;
              3: required set<i8> Bytes;
              4: required list<list<string>> listOfStrings
              5: required NonEmpty child;
            }
        """.trimIndent()

        val specs = generate(thrift) { emitJvmOverloads() }
        specs.shouldCompile()

        val struct = specs.single().members.single() as TypeSpec

        struct.name shouldBe "NonEmpty"
        struct.modifiers.any { it == KModifier.DATA } shouldBe true
        struct.funSpecs.any { it.name == "toString" } shouldBe false
        struct.funSpecs.any { it.name == "hashCode" } shouldBe false
        struct.funSpecs.any { it.name == "equals" } shouldBe false

        struct.toString() shouldContain  """
            |public data class NonEmpty @kotlin.jvm.JvmOverloads constructor(
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 1,
            |    isRequired = true,
            |  )
            |  public val Number: kotlin.Int = 0,
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 2,
            |    isRequired = true,
            |  )
            |  public val Text: kotlin.String = "",
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 3,
            |    isRequired = true,
            |  )
            |  public val Bytes: kotlin.collections.Set<kotlin.Byte> = kotlin.collections.emptySet(),
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 4,
            |    isRequired = true,
            |  )
            |  public val listOfStrings: kotlin.collections.List<kotlin.collections.List<kotlin.String>> = kotlin.collections.emptyList(),
            |  @kotlin.jvm.JvmField
            |  @jp.co.gahojin.thrifty.ThriftField(
            |    fieldId = 5,
            |    isRequired = true,
            |  )
            |  public val child: com.test.NonEmpty,
            |) : jp.co.gahojin.thrifty.Struct {
            """.trimMargin()
    }

    @Test
    fun `struct with docs is a data class with docs`() {
        val thrift = """
            namespace kt com.test

            /** Docs */
            struct NonEmpty {
              1: required i32 Number
            }
        """.trimIndent()

        val specs = generate(thrift)
        specs.shouldCompile()

        val struct = specs.single().members.single() as TypeSpec

        struct.name shouldBe "NonEmpty"
        struct.modifiers.any { it == KModifier.DATA } shouldBe true
        struct.kdoc.isNotEmpty() shouldBe true
        struct.kdoc.toString().trim() shouldBe "Docs"
    }

    @Test
    fun `struct field with docs is a data class property with docs`() {
        val thrift = """
            namespace kt com.test
           
            struct NonEmpty {
              /** Docs */
              1: required i32 Number
            }
        """.trimIndent()

        val specs = generate(thrift)
        specs.shouldCompile()

        val struct = specs.single().members.single() as TypeSpec

        struct.name shouldBe "NonEmpty"
        struct.modifiers.any { it == KModifier.DATA } shouldBe true
        struct.kdoc.isEmpty() shouldBe true
        struct.propertySpecs shouldHaveSize 1
        val propertySpec = struct.propertySpecs.first()
        propertySpec.kdoc.isNotEmpty() shouldBe true
        propertySpec.kdoc.toString().trim() shouldBe "Docs"
    }

    @Test
    fun `exceptions with reserved field names get renamed fields`() {
        val thrift = """
            namespace kt com.test

            exception Fail { 1: required list<i32> Message }
        """.trimIndent()

        val schema = load(thrift)
        val specs = KotlinCodeGenerator(FieldNamingPolicy.JAVA).generate(schema)
        specs.shouldCompile()
        val xception = specs.single().members.single() as TypeSpec
        xception.propertySpecs.single().name shouldBe "message_"
    }

    @Test
    fun services() {
        val thrift = """
            namespace kt test.services

            struct Foo { 1: required string foo; }
            struct Bar { 1: required string bar; }
            exception X { 1: required string message; }
            service Svc {
              void doThingOne(1: Foo foo) throws (2: X xxxx)
              Bar doThingTwo(1: Foo foo) throws (1: X x)

            }
        """.trimIndent()

        val specs = generate(thrift)
        specs.shouldCompile()
    }

    @Test
    fun server() {
        val thrift = """
            namespace kt test.services

            struct Foo { 1: required string foo; }
            struct Bar { 1: required string bar; }
            exception X { 1: required string message; }
            service Svc {
              void doThingOne(1: Foo foo) throws (2: X xxxx)
              Bar doThingTwo(1: Foo foo) throws (1: X x)
            }
        """.trimIndent()

        val specs = generate(thrift) {
            generateServer()
        }
        specs.shouldCompile()
    }

    @Test
    fun `typedefs become typealiases`() {
        val thrift = """
            namespace kt test.typedefs

            typedef map<i32, map<string, double>> FooMap;

            struct HasMap {
              1: optional FooMap theMap;
            }
        """.trimIndent()

        val specs = generate(thrift)
        specs.shouldCompile()
    }

    @Test
    fun `services that return typedefs`() {
        val thrift = """
            namespace kt test.typedefs

            typedef i32 TheNumber;
            service Foo {
              TheNumber doIt()
            }
        """.trimIndent()

        val file = generate(thrift).single()
        file.shouldCompile()
        val svc = file.members.first { it is TypeSpec && it.name == "Foo" } as TypeSpec
        val method = svc.funSpecs.single()
        method.modifiers.any { it == KModifier.SUSPEND } shouldBe true
        method.name shouldBe "doIt"
        method.parameters shouldHaveSize 0
        method.returnType shouldBe ClassName("test.typedefs", "TheNumber")
    }

    @Test
    fun `constants that are typedefs`() {
        val thrift = """
            |namespace kt test.typedefs
            |
            |typedef map<i32, i32> Weights
            |
            |const Weights WEIGHTS = {1: 2}
        """.trimMargin()

        "${generate(thrift).single()}" shouldBe """
            |package test.typedefs
            |
            |import kotlin.Int
            |import kotlin.collections.Map
            |
            |public typealias Weights = Map<Int, Int>
            |
            |public val WEIGHTS: Weights = mapOf(1 to 2)
            |
        """.trimMargin()
    }

    @Test
    fun `Parcelize annotations for structs and enums`() {
        val thrift = """
            |namespace kt test.parcelize
            |
            |struct Foo { 1: required i32 Number; 2: optional string Text }
            |
            |enum AnEnum { ONE; TWO; THREE }
            |
            |service Svc {
            |  Foo getFoo(1: AnEnum anEnum)
            |}
        """.trimMargin()

        val file = generate(thrift) { parcelize() }.single()
        val struct = file.members.single { it is TypeSpec && it.name == "Foo" } as TypeSpec
        val anEnum = file.members.single { it is TypeSpec && it.name == "AnEnum" } as TypeSpec
        val svc = file.members.single { it is TypeSpec && it.name == "SvcClient" } as TypeSpec
        val parcelize = ClassName("kotlinx.parcelize", "Parcelize")
        val parcelable = ClassName("android.os", "Parcelable")

        struct.annotations.any { it.typeName == parcelize } shouldBe true
        anEnum.annotations.any { it.typeName == parcelize } shouldBe true
        svc.annotations.any { it.typeName == parcelize } shouldBe false

        struct.superinterfaces.any { it.key == parcelable } shouldBe true
        anEnum.superinterfaces.any { it.key == parcelable } shouldBe true
        svc.superinterfaces.any { it.key == parcelable } shouldBe false
    }

    @Test
    fun `Custom map-type constants`() {
        val thrift = """
            |namespace kt test.map_consts
            |
            |const map<i32, list<string>> Maps = {1: [], 2: ["foo"]}
        """.trimMargin()

        val file = generate(thrift) {
            mapClassName("android.support.v4.util.ArrayMap")
            emitFileComment(false)
        }

        file.single().toString() shouldBe """
            |package test.map_consts
            |
            |import android.support.v4.util.ArrayMap
            |import kotlin.Int
            |import kotlin.String
            |import kotlin.collections.List
            |import kotlin.collections.Map
            |
            |public val Maps: Map<Int, List<String>> = ArrayMap<Int, List<String>>(2).apply {
            |      put(1, emptyList())
            |      put(2, listOf("foo"))
            |    }
            |
            """.trimMargin()
    }

    @Test
    fun `suspend-fun service clients`() {
        val thrift = """
            |namespace kt test.coro
            |
            |service Svc {
            |  i32 doSomething(1: i32 foo);
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |public interface Svc {
            |  public suspend fun doSomething(foo: Int): Int
            |}
            |
            |public class SvcClient(
            |  protocol: Protocol,
            |  listener: AsyncClientBase.Listener,
            |) : AsyncClientBase(protocol, listener),
            |    Svc {
            |  override suspend fun doSomething(foo: Int): Int = suspendCoroutine { cont ->
            |    this.enqueue(DoSomethingCall(foo, object : ServiceMethodCallback<Int> {
            |      override fun onSuccess(result: Int) {
            |        cont.resumeWith(Result.success(result))
            |      }
            |
            |      override fun onError(error: Throwable) {
            |        cont.resumeWith(Result.failure(error))
            |      }
            |    }))
            |  }
            |
        """.trimMargin())
    }

    @Test
    fun `omit service clients`() {
        val thrift = """
            |namespace kt test.omit_service_clients
            |
            |service Svc {
            |  i32 doSomething(1: i32 foo);
            |}
        """.trimMargin()

        val file = generate(thrift) { omitServiceClients() }

        file shouldBe emptyList()
    }

    @Test
    fun `omit struct implements`() {
        val thrift = """
            |namespace kt test.omit_service_clients
            |
            |struct Foo { 1: required i32 Number; 2: optional string Text }
        """.trimMargin()

        val file = generate(thrift) { omitStructImplements() }

        file.single().toString() should contain("""
            |) {
            |  private class FooAdapter : Adapter<Foo> {
        """.trimMargin())
    }

    @Test
    fun `Emit @JvmName file-per-namespace annotations`() {
        val thrift = """
            |namespace kt test.consts
            |
            |const i32 FooNum = 42
        """.trimMargin()

        val file = generate(thrift) {
            emitJvmName()
            filePerNamespace()
            emitFileComment(false)
        }.single()

        file.shouldCompile()

        file.toString() shouldBe """
            |@file:JvmName("ThriftTypes")
            |
            |package test.consts
            |
            |import kotlin.Int
            |import kotlin.jvm.JvmName
            |
            |public const val FooNum: Int = 42
            |
            """.trimMargin()
    }

    @Test
    fun `Emit @JvmName file-per-type annotations`() {
        val thrift = """
            |namespace kt test.consts
            |
            |const i32 FooNum = 42
        """.trimMargin()

        val file = generate(thrift) {
            emitJvmName()
            filePerType()
            emitFileComment(false)
        }.single()

        file.shouldCompile()

        file.toString() shouldBe """
            |@file:JvmName("Constants")
            |
            |package test.consts
            |
            |import kotlin.Int
            |import kotlin.jvm.JvmName
            |
            |public const val FooNum: Int = 42
            |
            """.trimMargin()
    }

    @Test
    fun `union generate sealed`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |  1: i32 Foo;
            |  2: i64 Bar;
            |  3: string Baz;
            |  4: i32 NotFoo;
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |sealed class Union : Struct {
        """.trimMargin())
    }

    @Test
    fun `union properties as data`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |  1: i32 Foo;
            |  2: i64 Bar;
            |  3: string Baz;
            |  4: i32 NotFoo;
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |
            |  public data class Foo(
            |    public val `value`: Int,
            |  ) : Union() {
            |    override fun toString(): String = "Union(Foo=${'$'}value)"
            |  }
            |
            |  public data class Bar(
            |    public val `value`: Long,
            |  ) : Union() {
            |    override fun toString(): String = "Union(Bar=${'$'}value)"
            |  }
            |
            |  public data class Baz(
            |    public val `value`: String,
            |  ) : Union() {
            |    override fun toString(): String = "Union(Baz=${'$'}value)"
            |  }
            |
            |  public data class NotFoo(
            |    public val `value`: Int,
            |  ) : Union() {
            |    override fun toString(): String = "Union(NotFoo=${'$'}value)"
            |  }
            |
        """.trimMargin())
    }

    @Test
    fun `union wont generate builder when disabled`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |  1: i32 Foo;
            |  2: i64 Bar;
            |  3: string Baz;
            |  4: i32 NotFoo;
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() shouldNot contain("""
            |    class Builder
        """.trimMargin())
    }

    @Test
    fun `union wont generate struct when disabled`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |  1: i32 Foo;
            |  2: i64 Bar;
            |  3: string Baz;
            |  4: i32 NotFoo;
            |}
        """.trimMargin()

        val file = generate(thrift) //{ shouldImplementStruct() }
        file.shouldCompile()

        file.single().toString() shouldNot contain("""
            |  : Struct
        """.trimMargin())

        file.single().toString() shouldNot contain("""
            |  write
        """.trimMargin())
    }

     @Test
    fun `union generate read function`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |  1: i32 Foo;
            |  2: i64 Bar;
            |  3: string Baz;
            |  4: i32 NotFoo;
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |    override fun read(protocol: Protocol): Union {
            |      protocol.readStructBegin()
            |      var result : Union? = null
            |      while (true) {
            |        val fieldMeta = protocol.readFieldBegin()
            |        if (fieldMeta.typeId == TType.STOP) {
            |          break
            |        }
            |        when (fieldMeta.fieldId.toInt()) {
            |          1 -> {
            |            if (fieldMeta.typeId == TType.I32) {
            |              val Foo = protocol.readI32()
            |              result = Foo(Foo)
            |            } else {
            |              protocol.skip(fieldMeta.typeId)
            |            }
            |          }
            |          2 -> {
            |            if (fieldMeta.typeId == TType.I64) {
            |              val Bar = protocol.readI64()
            |              result = Bar(Bar)
            |            } else {
            |              protocol.skip(fieldMeta.typeId)
            |            }
            |          }
            |          3 -> {
            |            if (fieldMeta.typeId == TType.STRING) {
            |              val Baz = protocol.readString()
            |              result = Baz(Baz)
            |            } else {
            |              protocol.skip(fieldMeta.typeId)
            |            }
            |          }
            |          4 -> {
            |            if (fieldMeta.typeId == TType.I32) {
            |              val NotFoo = protocol.readI32()
            |              result = NotFoo(NotFoo)
            |            } else {
            |              protocol.skip(fieldMeta.typeId)
            |            }
            |          }
            |          else -> protocol.skip(fieldMeta.typeId)
            |        }
            |        protocol.readFieldEnd()
            |      }
            |      protocol.readStructEnd()
            |      return checkNotNull(result) { "unreadable" }
            |    }
        """.trimMargin())
    }

    @Test
    fun `union generate Adapter`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |  1: i32 Foo;
            |  2: i64 Bar;
            |  3: string Baz;
            |  4: i32 NotFoo;
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |  private class UnionAdapter : Adapter<Union> {
        """.trimMargin())
    }

    @Test
    fun `empty union generate non-sealed class`() {
        val thrift = """
            |namespace kt test.coro
            |
            |union Union {
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |class Union() : Struct {
        """.trimMargin())
    }

    @Test
    fun `struct with union`() {
        val thrift = """
            |namespace kt test.coro
            |
            |struct Bonk {
            |  1: string message;
            |  2: i32 type;
            |}
            |
            |union UnionStruct {
            |  1: Bonk Struct
            |}
        """.trimMargin()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |public sealed class UnionStruct : Struct {
            |  override fun write(protocol: Protocol) {
            |    ADAPTER.write(protocol, this)
            |  }
            |
            |  public data class Struct(
            |    public val `value`: Bonk,
            |  ) : UnionStruct() {
            |    override fun toString(): String = "UnionStruct(Struct=${'$'}value)"
            |  }
            |
            |  private class UnionStructAdapter : Adapter<UnionStruct> {
        """.trimMargin())
    }

    @Test
    fun `union with default value`() {
        val thrift = """
            namespace kt test.union

            union HasDefault {
                1: i8 b;
                2: i16 short;
                3: i32 int = 16;
                4: i64 long;
            }
        """.trimIndent()

        val file = generate(thrift)
        file.shouldCompile()

        file.single().toString() should contain("""
            |    @JvmField
            |    public val DEFAULT: HasDefault = Int(16)
        """.trimMargin())
    }

    @Test
    fun `enum fail on unknown value`() {
        val thrift = """
            |namespace kt test.struct
            |
            |enum TestEnum { FOO }
            |
            |struct HasEnum {
            |  1: optional TestEnum field = TestEnum.FOO;
            |}
        """.trimMargin()

        val expected = """
          1 -> {
            if (fieldMeta.typeId == TType.I32) {
              val field_ = protocol.readI32().let {
                TestEnum.findByValue(it) ?: throw ThriftException(ThriftException.Kind.PROTOCOL_ERROR, "Unexpected value for enum type TestEnum: ${'$'}it")
              }
              _local_field = field_
            } else {
              protocol.skip(fieldMeta.typeId)
            }
          }"""

        val file = generate(thrift)
        file.shouldCompile()
        file.single().toString() shouldContain expected
    }

    @Test
    fun `enum don't fail on unknown value`() {
        val thrift = """
            |namespace kt test.struct
            |
            |enum TestEnum { FOO }
            |
            |struct HasEnum {
            |  1: optional TestEnum field1 = TestEnum.FOO;
            |  2: required TestEnum field2 = TestEnum.FOO;
            |}
        """.trimMargin()

        val expected = """
          1 -> {
            if (fieldMeta.typeId == TType.I32) {
              val field1 = protocol.readI32().let {
                TestEnum.findByValue(it)
              }
              field1?.let {
                _local_field1 = it
              }
            } else {
              protocol.skip(fieldMeta.typeId)
            }
          }
          2 -> {
            if (fieldMeta.typeId == TType.I32) {
              val field2 = protocol.readI32().let {
                TestEnum.findByValue(it) ?: throw ThriftException(ThriftException.Kind.PROTOCOL_ERROR, "Unexpected value for enum type TestEnum: ${'$'}it")
              }
              _local_field2 = field2
            } else {
              protocol.skip(fieldMeta.typeId)
            }
          }"""

        val file = generate(thrift) { failOnUnknownEnumValues(false) }
        file.shouldCompile()
        file.single().toString() shouldContain expected
    }

    @Test
    fun `collection types do not use Java collections by default`() {
        val thrift = """
            |namespace kt test.lists
            |
            |const list<i32> FOO = [1, 2, 3];
            |const map<i8, i8> BAR = { 1: 2 };
            |const set<string> BAZ = ["foo", "bar", "baz"];
            |
            |struct HasCollections {
            |  1: list<string> strs;
            |  2: map<string, string> more_strs;
            |  3: set<i16> shorts;
            |}
            |
            |service HasListMethodArg {
            |  list<i8> sendThatList(1: list<i8> byteList);
            |}
        """.trimMargin()

        for (file in generate(thrift)) {
            val kt = file.toString()
            kt shouldNotContain "java.util"
        }
    }

    @Test
    fun `does not import java Exception or IOException`() {
        val thrift = """
            |namespace kt test.exceptions
            |
            |exception Foo {
            |  1: string message;
            |}
        """.trimMargin()

        for (file in generate(thrift)) {
            val kt = file.toString()

            kt shouldNotContain "import java.Exception"
            kt shouldNotContain "import java.io.IOException"
        }
    }

    @Test
    fun `uses default Throws instead of jvm Throws`() {
        val thrift = """
            |namespace kt test.throws
            |
            |service Frobbler {
            |  void frobble(1: string bizzle);
            |}
        """.trimMargin()

        for (file in generate(thrift)) {
            val kt = file.toString()

            kt shouldContain "@Throws"
            kt shouldNotContain "import kotlin.jvm.Throws"
        }
    }

    @Test
    fun `empty structs do not rely on javaClass for hashCode`() {
        val thrift = """
            |namespace kt test.empty
            |
            |struct Empty {}
        """.trimMargin()

        val file = generate(thrift).single()
        val kt = file.toString()

        kt shouldContain "hashCode(): Int = \"test.empty.Empty\".hashCode()"
        kt shouldNotContain "javaClass"
    }

    @Test
    fun `big enum generation`() {
        val thrift = """
            namespace kt test.enum
            
            enum Foo {
              FIRST_VALUE = 0,
              SECOND_VALUE = 1,
              THIRD_VALUE = 2
            }
        """.trimIndent()

        val expected = """
            |public enum class Foo {
            |  FIRST_VALUE,
            |  SECOND_VALUE,
            |  THIRD_VALUE,
            |  ;
            |
            |  public val `value`: Int
            |    get() = `value`()
            |
            |  public fun `value`(): Int = when (this) {
            |    FIRST_VALUE -> 0
            |    SECOND_VALUE -> 1
            |    THIRD_VALUE -> 2
            |  }
            |
            |  public companion object {
            |    public fun findByValue(`value`: Int): Foo? = when (`value`) {
            |      0 -> FIRST_VALUE
            |      1 -> SECOND_VALUE
            |      2 -> THIRD_VALUE
            |      else -> null
            |    }
            |  }
            |}
        """.trimMargin()

        val notExpected = """
            public enum class Foo(value: Int)
        """.trimIndent()

        val file = generate(thrift) { emitBigEnums() }
        file.single().toString() shouldContain expected
        file.single().toString() shouldNotContain notExpected
    }

    @Test
    fun `struct-valued constant`() {
        val thrift = """
            |namespace kt test.struct
            |
            |struct Foo {
            |  1: string text;
            |  2: Bar bar;
            |  3: Baz baz;
            |  4: Quux quux;
            |}
            |
            |struct Bar {
            |  1: map<string, list<string>> keys;
            |}
            |
            |enum Baz {
            |  ONE,
            |  TWO,
            |  THREE
            |}
            |
            |struct Quux {
            |  1: string s;
            |}
            |
            |const Quux THE_QUUX = {
            |  "s": "s"
            |}
            |
            |const Foo THE_FOO = {
            |  "text": "some text",
            |  "bar": {
            |    "keys": {
            |      "letters": ["a", "b", "c"],
            |    }
            |  },
            |  "baz": Baz.ONE,
            |  "quux": THE_QUUX
            |}
        """.trimMargin()

        val expected = """
            |public val THE_FOO: Foo = Foo(
            |      text = "some text",
            |      bar = Bar(
            |        keys = mapOf("letters" to listOf("a", "b", "c")),
            |      ),
            |      baz = test.struct.Baz.ONE,
            |      quux = test.struct.THE_QUUX,
            |    )
        """.trimMargin()

        val file = generate(thrift).single()

        file.toString() shouldContain expected
        file.shouldCompile()
    }

    @Test
    fun `Files should add generated comment`() {
        val thrift = """
            |namespace kt test.comment
            |
            |const i32 FooNum = 42
        """.trimMargin()

        val file = generate(thrift) { emitFileComment(true) }
                .single()
        file.shouldCompile()

        val lines = file.toString().split("\n")
        lines[0] shouldBe "// Automatically generated by the Thrifty compiler; do not edit!"
        lines[1] shouldMatch """// Generated on: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\S+)?Z""".toRegex()
    }

    @Test
    fun `struct const with default field value using data classes`() {
        val thrift = """
            |namespace kt test.struct_const.default_fields
            |
            |struct Foo {
            |  1: required string text = "FOO";
            |  2: required i32 number;
            |}
            |
            |const Foo THE_FOO = {"number": 42}
        """.trimMargin()

        val file = generate(thrift)

        file.toString() shouldContain """
            |public val THE_FOO: Foo = Foo(
            |      number = 42,
            |    )
        """.trimMargin()
    }

    @Test
    fun `constant reference`() {
        val thrift = """
            |namespace kt test.const_ref
            |
            |struct Node {
            |  1: optional list<Node> n;
            |}
            |
            |const Node D = {"n": [B, C]}
            |const Node C = {"n": [A]}
            |const Node B = {"n": [A]}
            |const Node A = {}
        """.trimMargin()

        val files = generate(thrift) { filePerType() }
        val constants = files.single { it.name.contains("Constants") }
        val kotlinText = constants.toString()

        val positionOfA = kotlinText.indexOf("val A: Node")
        val positionOfD = kotlinText.indexOf("val D: Node")

        positionOfA shouldBeLessThan positionOfD

        files.shouldCompile()
    }

    @Test
    fun `union with builder should compile`() {
        val thrift = """
            |namespace kt test.union
            |
            |union Union {
            |  1: i32 result;
            |  2: i64 bigResult;
            |  3: string error;
            |}
        """.trimMargin()

        val files = generate(thrift)

        files.shouldCompile()

        println(files)
    }

    @Test
    fun `generate data class with nullable fields`() {
        val thrift = """
            |namespace kt test.struct_const.default_fields
            |
            |struct Foo {
            |  1: string text = "FOO";
            |  2: string text2;
            |  3: i32 number;
            |}
            |
            |const Foo THE_FOO = {"text": "FOO"}
        """.trimMargin()

        val file = generate(thrift)

        file.toString() shouldContain """
            |public val THE_FOO: Foo = Foo(
            |      text = "FOO",
            |    )
            |
            |public data class Foo(
            |  @JvmField
            |  @ThriftField(fieldId = 1)
            |  public val text: String? = "FOO",
            |  @JvmField
            |  @ThriftField(fieldId = 2)
            |  public val text2: String? = null,
            |  @JvmField
            |  @ThriftField(fieldId = 3)
            |  public val number: Int? = null,
            |) : Struct {
        """.trimMargin()
    }

    @Test
    fun `generate data class with mutable fields`() {
        val thrift = """
            |namespace kt test.struct_const.mutable_fields
            |
            |struct Foo {
            |  1: string text = "FOO";
            |  2: string text2;
            |  3: i32 number;
            |}
            |
            |const Foo THE_FOO = {"text": "FOO"}
        """.trimMargin()

        val file = generate(thrift) { mutableFields() }

        file.toString() shouldContain """
            |public val THE_FOO: Foo = Foo(
            |      text = "FOO",
            |    )
            |
            |public data class Foo(
            |  @JvmField
            |  @ThriftField(fieldId = 1)
            |  public var text: String? = "FOO",
            |  @JvmField
            |  @ThriftField(fieldId = 2)
            |  public var text2: String? = null,
            |  @JvmField
            |  @ThriftField(fieldId = 3)
            |  public var number: Int? = null,
            |) : Struct {
        """.trimMargin()
    }

    @Test
    fun `generate data class with clear method`() {
        val thrift = """
            namespace kt clear_method

            // This is an enum
            enum MyEnum {
              MEMBER_ONE, // trailing doc
              MEMBER_TWO
            }
            
            struct foo {
                1: string bar
                2: required i32 number;
                3: required i32 test = 100;
                4: required foo foo;
                5: MyEnum enumType;
                6: set<i8> Bytes;
                7: required list<list<string>> listOfStrings
                8: map<i64, foo> Maps
                9: map<set<i8>, foo> NestedMaps
            }
        """

        val file = generate(thrift) { emitDeepCopyFunc() }
        file.toString() shouldContain """
            |  public fun deepCopy(): foo = foo(
            |    bar = bar,
            |    number = number,
            |    test = test,
            |    foo = foo.deepCopy(),
            |    enumType = enumType,
            |    Bytes = Bytes?.let { LinkedHashSet(it) },
            |    listOfStrings = listOfStrings.map { i1 -> ArrayList(i1) },
            |    Maps = Maps?.entries.associate { i1 -> i1.key to i1.value.deepCopy() },
            |    NestedMaps = NestedMaps?.entries.associate { i1 -> LinkedHashSet(i1.key) to i1.value.deepCopy() },
            |  )
            |""".trimMargin()
    }

    @Test
    fun `generate data class with deepCopy method`() {
        val thrift = """
            namespace kt deep_copy_method

            struct foo {
                1: string bar
                2: required i32 number;
                3: required i32 test = 100;
            }
        """

        val file = generate(thrift) { mutableFields() }
        file.toString() shouldContain """
            |  public fun clear() {
            |    bar = null
            |    number = 0
            |    test = 100
            |  }
            |""".trimMargin()
    }


    @Test
    fun `jvmSuppressWildcard to be attached to the collection type`() {
        val thrift = """
            |namespace kt test.lists
            |
            |const list<i32> FOO = [1, 2, 3];
            |const map<i8, i8> BAR = { 1: 2 };
            |const set<string> BAZ = ["foo", "bar", "baz"];
            |
            |struct HasCollections {
            |  1: list<string> strs;
            |  2: map<string, string> more_strs;
            |  3: set<i16> shorts;
            |}
            |
            |service HasListMethodArg {
            |  list<i8> sendThatList(1: list<i8> byteList);
            |}
        """.trimMargin()

        val file = generate(thrift) { emitJvmSuppressWildcards() }
        file.toString() shouldContain ": @JvmSuppressWildcards "
    }

    private fun generate(
        thrift: String,
        config: (KotlinCodeGenerator.() -> KotlinCodeGenerator) = { emitFileComment(false) },
    ): List<FileSpec> {
        return KotlinCodeGenerator()
                .run(config)
                .generate(load(thrift))
    }

    private fun load(thrift: String): Schema {
        val file = File(tempDir, "test.thrift").also { it.writeText(thrift) }
        val loader = Loader().apply { addThriftFile(file.toPath()) }
        return loader.load()
    }
}
