/*
 * TestThrift.thrift, modified to use a separate package name.
 *
 * Any changes to this file *MUST* be mirrored in thrifty-test-server's copy!
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Contains some contributions under the Thrift Software License.
 * Please see doc/old-thrift-license.txt in the Thrift distribution for
 * details.
 */

namespace kt jp.co.gahsssojin.thrifty.runtime.kgen.coro

/**
 * Docstring!
 */
enum Numberz
{
  ONE = 1,
  TWO,
  THREE,
  FIVE = 5,
  SIX,
  EIGHT = 8
}

const double ActualDouble = 42

const Numberz myNumberz = Numberz.ONE;

typedef i64 UserId

struct Bonk
{
  1: string message,
  2: i32 type
}

struct Xtruct
{
  1:  string string_thing,
  4:  byte   byte_thing,
  9:  i32    i32_thing,
  11: i64    i64_thing,
  13: double double_thing,
  15: bool   bool_thing
}

struct Xtruct2
{
  1: byte     byte_thing,  // used to be byte, hence the name
  2: Xtruct struct_thing,
  3: i32    i32_thing
}

struct Insanity
{
  1: map<Numberz, UserId> userMap,
  2: list<Xtruct> xtructs
}

exception Xception {
  1: i32 errorCode,
  2: string message
}

exception Xception2 {
  1: i32 errorCode,
  2: Xtruct struct_thing
}

union TheEmptyUnion {}

union NonEmptyUnion {
  1: i32 AnInt;
  2: i64 ALong;
  3: string AString;
  4: Bonk ABonk;
}

struct HasUnion {
    1: required NonEmptyUnion TheUnion;
}

union UnionWithDefault {
  1: string Text;
  2: i32 Int;
  3: double Real = 3.14
}

union UnionWithRedactions {
  1: string text;
  2: string obfuscated_text (obfuscated = "true");
  3: string redacted_text (redacted = "true");
  4: list<i32> nums;
  5: list<i32> obfuscated_nums (obfuscated = "true");
  6: list<i32> redacted_nums (redacted = "true");
  7: set<double> dubs;
  8: set<double> obfuscated_dubs (obfuscated = "true");
  9: set<double> redacted_dubs (redacted = "true");
  10: map<i8, i8> bytes;
  11: map<i8, i8> obfuscated_bytes (obfuscated = "true");
  12: map<i8, i8> redacted_bytes (redacted = "true");
}

service ThriftTest
{
  /**
   * Prints "testVoid()" and returns nothing.
   */
  void         testVoid(),

  /**
   * Prints 'testString("%s")' with thing as '%s'
   * @param string thing - the string to print
   * @return string - returns the string 'thing'
   */
  string       testString(1: string thing),

  /**
   * Prints 'testBool("%s")' where '%s' with thing as 'true' or 'false'
   * @param bool  thing - the bool data to print
   * @return bool  - returns the bool 'thing'
   */
  bool         testBool(1: bool thing),

  /**
   * Prints 'testByte("%d")' with thing as '%d'
   * The types i8 and byte are synonyms, use of i8 is encouraged, byte still exists for the sake of compatibility.
   * @param byte thing - the i8/byte to print
   * @return i8 - returns the i8/byte 'thing'
   */
  byte         testByte(1: byte thing),

  /**
   * Prints 'testI32("%d")' with thing as '%d'
   * @param i32 thing - the i32 to print
   * @return i32 - returns the i32 'thing'
   */
  i32          testI32(1: i32 thing),

  /**
   * Prints 'testI64("%d")' with thing as '%d'
   * @param i64 thing - the i64 to print
   * @return i64 - returns the i64 'thing'
   */
  i64          testI64(1: i64 thing),

  /**
   * Prints 'testDouble("%f")' with thing as '%f'
   * @param double thing - the double to print
   * @return double - returns the double 'thing'
   */
  double       testDouble(1: double thing),

  /**
   * Prints 'testBinary("%s")' where '%s' is a hex-formatted string of thing's data
   * @param binary  thing - the binary data to print
   * @return binary  - returns the binary 'thing'
   */
  binary       testBinary(1: binary thing),

  /**
   * Prints 'testStruct("{%s}")' where thing has been formatted into a string of comma separated values
   * @param Xtruct thing - the Xtruct to print
   * @return Xtruct - returns the Xtruct 'thing'
   */
  Xtruct       testStruct(1: Xtruct thing),

  /**
   * Prints 'testNest("{%s}")' where thing has been formatted into a string of the nested struct
   * @param Xtruct2 thing - the Xtruct2 to print
   * @return Xtruct2 - returns the Xtruct2 'thing'
   */
  Xtruct2      testNest(1: Xtruct2 thing),

  /**
   * Prints 'testMap("{%s")' where thing has been formatted into a string of  'key => value' pairs
   *  separated by commas and new lines
   * @param map<i32,i32> thing - the map<i32,i32> to print
   * @return map<i32,i32> - returns the map<i32,i32> 'thing'
   */
  map<i32,i32> testMap(1: map<i32,i32> thing),

  /**
   * Prints 'testStringMap("{%s}")' where thing has been formatted into a string of  'key => value' pairs
   *  separated by commas and new lines
   * @param map<string,string> thing - the map<string,string> to print
   * @return map<string,string> - returns the map<string,string> 'thing'
   */
  map<string,string> testStringMap(1: map<string,string> thing),

  /**
   * Prints 'testSet("{%s}")' where thing has been formatted into a string of  values
   *  separated by commas and new lines
   * @param set<i32> thing - the set<i32> to print
   * @return set<i32> - returns the set<i32> 'thing'
   */
  set<i32>     testSet(1: set<i32> thing),

  /**
   * Prints 'testList("{%s}")' where thing has been formatted into a string of  values
   *  separated by commas and new lines
   * @param list<i32> thing - the list<i32> to print
   * @return list<i32> - returns the list<i32> 'thing'
   */
  list<i32>    testList(1: list<i32> thing),

  /**
   * Prints 'testEnum("%d")' where thing has been formatted into it's numeric value
   * @param Numberz thing - the Numberz to print
   * @return Numberz - returns the Numberz 'thing'
   */
  Numberz      testEnum(1: Numberz thing),

  /**
   * Prints 'testTypedef("%d")' with thing as '%d'
   * @param UserId thing - the UserId to print
   * @return UserId - returns the UserId 'thing'
   */
  UserId       testTypedef(1: UserId thing),

  /**
   * Prints 'testMapMap("%d")' with hello as '%d'
   * @param i32 hello - the i32 to print
   * @return map<i32,map<i32,i32>> - returns a dictionary with these values:
   *   {-4 => {-4 => -4, -3 => -3, -2 => -2, -1 => -1, }, 4 => {1 => 1, 2 => 2, 3 => 3, 4 => 4, }, }
   */
  map<i32,map<i32,i32>> testMapMap(1: i32 hello),

  /**
   * So you think you've got this all worked, out eh?
   *
   * Creates a the returned map with these values and prints it out:
   *   { 1 => { 2 => argument,
   *            3 => argument,
   *          },
   *     2 => { 6 => <empty Insanity struct>, },
   *   }
   * @return map<UserId, map<Numberz,Insanity>> - a map with the above values
   */
  map<UserId, map<Numberz,Insanity>> testInsanity(1: Insanity argument),

  /**
   * Prints 'testMulti()'
   * @param byte arg0 -
   * @param i32 arg1 -
   * @param i64 arg2 -
   * @param map<i16, string> arg3 -
   * @param Numberz arg4 -
   * @param UserId arg5 -
   * @return Xtruct - returns an Xtruct with string_thing = "Hello2, byte_thing = arg0, i32_thing = arg1
   *    and i64_thing = arg2
   */
  Xtruct testMulti(1: byte arg0, 2: i32 arg1, 3: i64 arg2, 4: map<i16, string> arg3, 5: Numberz arg4, 6: UserId arg5),

  /**
   * Print 'testException(%s)' with arg as '%s'
   * @param string arg - a string indication what type of exception to throw
   * if arg == "Xception" throw Xception with errorCode = 1001 and message = arg
   * elsen if arg == "TException" throw TException
   * else do not throw anything
   */
  void testException(1: string arg) throws(1: Xception err1),

  /**
   * Print 'testMultiException(%s, %s)' with arg0 as '%s' and arg1 as '%s'
   * @param string arg - a string indication what type of exception to throw
   * if arg0 == "Xception" throw Xception with errorCode = 1001 and message = "This is an Xception"
   * elsen if arg0 == "Xception2" throw Xception2 with errorCode = 2002 and struct_thing.string_thing = "This is an Xception2"
   * else do not throw anything
   * @return Xtruct - an Xtruct with string_thing = arg1
   */
  Xtruct testMultiException(1: string arg0, 2: string arg1) throws(1: Xception err1, 2: Xception2 err2)

  /**
   * Print 'testOneway(%d): Sleeping...' with secondsToSleep as '%d'
   * sleep 'secondsToSleep'
   * Print 'testOneway(%d): done sleeping!' with secondsToSleep as '%d'
   * @param i32 secondsToSleep - the number of seconds to sleep
   */
  oneway void testOneway(1:i32 secondsToSleep)

  /**
   * Prints 'testUnionArgument()' and returns the argument unmodified, wrapped in a
   * HasUnion struct.
   **/
  HasUnion testUnionArgument(1: NonEmptyUnion arg0)

  /**
   * Returns the argument unaltered.
   */
  UnionWithDefault testUnionWithDefault(1: UnionWithDefault theArg)
}

// Builderless unions should handle fields named "result".
// see https://github.com/microsoft/thrifty/issues/404
union UnionWithResult {
  1: i32 result;
  2: i64 bigResult;
  3: string error;
}

const Insanity TOTAL_INSANITY = {
  "userMap": {
    myNumberz: 1234
  },
  "xtructs": [
    {
      "string_thing": "hello",
    },
    {
      "i32_thing": 1,
      "bool_thing": 0,
    },
  ]
}

const Bonk A_BONK = {
  "message": "foobar",
  "type": 100,
}
