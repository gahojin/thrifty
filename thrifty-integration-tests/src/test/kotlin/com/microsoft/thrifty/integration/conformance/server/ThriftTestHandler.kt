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
package com.microsoft.thrifty.integration.conformance.server

import com.microsoft.thrifty.integration.kgen.HasUnion
import com.microsoft.thrifty.integration.kgen.Insanity
import com.microsoft.thrifty.integration.kgen.NonEmptyUnion
import com.microsoft.thrifty.integration.kgen.Numberz
import com.microsoft.thrifty.integration.kgen.server.ThriftTest
import com.microsoft.thrifty.integration.kgen.UnionWithDefault
import com.microsoft.thrifty.integration.kgen.UserId
import com.microsoft.thrifty.integration.kgen.Xception
import com.microsoft.thrifty.integration.kgen.Xception2
import com.microsoft.thrifty.integration.kgen.Xtruct
import com.microsoft.thrifty.integration.kgen.Xtruct2
import okio.ByteString
import org.apache.thrift.TException

class ThriftTestHandler : ThriftTest {
    override suspend fun testVoid() = Unit

    override suspend fun testString(thing: String): String {
        return thing
    }

    override suspend fun testBool(thing: Boolean): Boolean {
        return thing
    }

    override suspend fun testByte(thing: Byte): Byte {
        return thing
    }

    override suspend fun testI32(thing: Int): Int {
        return thing
    }

    override suspend fun testI64(thing: Long): Long {
        return thing
    }

    override suspend fun testDouble(thing: Double): Double {
        return thing
    }

    override suspend fun testBinary(thing: ByteString): ByteString {
        return thing
    }

    override suspend fun testStruct(thing: Xtruct): Xtruct {
        return thing
    }

    override suspend fun testNest(thing: Xtruct2): Xtruct2 {
        return thing
    }

    override suspend fun testMap(thing: Map<Int, Int>): Map<Int, Int> {
        return thing
    }

    override suspend fun testStringMap(thing: Map<String, String>): Map<String, String> {
        return thing
    }

    override suspend fun testSet(thing: Set<Int>): Set<Int> {
        return thing
    }

    override suspend fun testList(thing: List<Int>): List<Int> {
        return thing
    }

    override suspend fun testEnum(thing: Numberz): Numberz {
        return thing
    }

    override suspend fun testTypedef(thing: UserId): UserId {
        return thing
    }

    override suspend fun testMapMap(hello: Int): Map<Int, Map<Int, Int>> {
        // {-4 => {-4 => -4, -3 => -3, -2 => -2, -1 => -1, }, 4 => {1 => 1, 2 => 2, 3 => 3, 4 => 4, }, }

        // {-4 => {-4 => -4, -3 => -3, -2 => -2, -1 => -1, }, 4 => {1 => 1, 2 => 2, 3 => 3, 4 => 4, }, }
        val first = mapOf(
            -4 to -4,
            -3 to -3,
            -2 to -2,
            -1 to -1,
        )
        val second = mapOf(
            1 to 1,
            2 to 2,
            3 to 3,
            4 to 4,
        )

        return mapOf(
            -4 to first,
            4 to second,
        )
    }

    override suspend fun testInsanity(argument: Insanity): Map<UserId, Map<Numberz, Insanity>> {
        /*
         *   { 1 => { 2 => argument,
         *            3 => argument,
         *          },
         *     2 => { 6 => <empty Insanity struct>, },
         *   }
         */

        /*
         *   { 1 => { 2 => argument,
         *            3 => argument,
         *          },
         *     2 => { 6 => <empty Insanity struct>, },
         *   }
         */
        val first = mapOf<Numberz, Insanity>(
            Numberz.TWO to argument,
            Numberz.THREE to argument,
        )
        val second = mapOf<Numberz, Insanity>(
            Numberz.SIX to Insanity(null, null),
        )

        return mapOf(
            1L to first,
            2L to second,
        )
    }

    override suspend fun testMulti(
        arg0: Byte,
        arg1: Int,
        arg2: Long,
        arg3: Map<Short, String>,
        arg4: Numberz,
        arg5: UserId,
    ) = Xtruct("Hello2", arg0, arg1, arg2, null, null)

    override suspend fun testException(arg: String) {
        if ("TException" == arg) {
            throw TException()
        } else if ("Xception" == arg) {
            throw Xception(1001, "Xception")
        }
    }

    override suspend fun testMultiException(arg0: String, arg1: String): Xtruct {
        if ("Xception" == arg0) {
            throw Xception(1001, "This is an Xception")
        } else if ("Xception2" == arg0) {
            val xtruct = Xtruct(
                string_thing = "This is an Xception2",
                byte_thing = null,
                i32_thing = null,
                i64_thing = null,
                double_thing = null,
                bool_thing = null,
            )
            throw Xception2(2002, xtruct)
        }

        return Xtruct(
            string_thing = arg1,
            byte_thing = null,
            i32_thing = null,
            i64_thing = null,
            double_thing = null,
            bool_thing = null,
        )
    }

    override suspend fun testOneway(secondsToSleep: Int) = Unit

    override suspend fun testUnionArgument(arg0: NonEmptyUnion): HasUnion {
        val result = HasUnion(arg0)
        return result
    }

    override suspend fun testUnionWithDefault(theArg: UnionWithDefault): UnionWithDefault {
        return theArg
    }
}
