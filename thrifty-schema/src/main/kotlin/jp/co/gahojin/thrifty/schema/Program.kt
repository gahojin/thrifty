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
package jp.co.gahojin.thrifty.schema

import jp.co.gahojin.thrifty.schema.parser.ThriftFileElement

/**
 * A Program is the set of elements declared in a Thrift file.  It
 * contains all types, namespaces, constants, and inclusions defined therein.
 */
class Program internal constructor(element: ThriftFileElement) {

    /**
     * All namespaces defined for this [Program].
     */
    val namespaces = element.namespaces.associate { it.scope to it.namespace }

    /**
     * All `cpp_include` statements in this [Program].
     */
    val cppIncludes = element.includes.filter { it.isCpp }.map { it.path }

    private val thriftIncludes = element.includes.filter { !it.isCpp }.map { it.path }

    /**
     * All [constants][Constant] contained within this [Program]
     */
    val constants = element.constants.map { Constant(it, namespaces) }

    /**
     * All [enums][EnumType] contained within this [Program].
     */
    val enums = element.enums.map { EnumType(it, namespaces) }

    /**
     * All [structs][StructType] contained within this [Program].
     */
    val structs = element.structs.map { StructType(it, namespaces) }

    /**
     * All [unions][StructType] contained within this [Program].
     */
    val unions = element.unions.map { StructType(it, namespaces) }

    /**
     * All [exceptions][StructType] contained within this [Program].
     */
    val exceptions = element.exceptions.map { StructType(it, namespaces) }

    /**
     * All [typedefs][TypedefType] contained within this [Program].
     */
    val typedefs = element.typedefs.map { TypedefType(it, namespaces) }

    /**
     * All [services][ServiceType] contained within this [Program].
     */
    val services = element.services.map { ServiceType(it, namespaces) }

    /**
     * The location of this [Program], possibly relative (if it was loaded from the search path).
     */
    val location = element.location

    private var includedPrograms: List<Program>? = null
    private var constSymbols: Map<String, Constant>? = null

    /**
     * All other [programs][Program] included by this [Program].
     */
    val includes: List<Program>
        get() = includedPrograms ?: emptyList()

    /**
     * A map of constants in this program indexed by name.
     */
    val constantMap: Map<String, Constant>
        get() = constSymbols ?: emptyMap()

    /**
     * Get all named types declared in this Program.
     *
     * Note that this does not include [constants], which are
     * not types.
     *
     * @return all user-defined types contained in this Program.
     */
    fun allUserTypes(): Iterable<UserType> {
        return listOf(enums, structs, unions, exceptions, services, typedefs)
            .flatMapTo(mutableListOf()) { it }
    }

    /**
     * Loads this program's symbol table and list of included Programs.
     * @param loader
     * @param visited A [MutableMap] used to track a parent [Program], if it was visited from one.
     * @param parent The parent [Program] that is including this [Program],
     * `null` if this [Program] is not being loaded from another [Program].
     */
    internal fun loadIncludedPrograms(loader: Loader, visited: MutableMap<Program, Program?>, parent: Program?) {
        if (visited.containsKey(this)) {
            if (includedPrograms == null) {
                val includeChain = StringBuilder(this.location.programName)
                var current: Program? = parent
                while (current != null) {
                    includeChain.append(" -> ")
                    includeChain.append(current.location.programName)
                    if (current == this) {
                        break
                    }
                    current = visited[current]
                }
                loader.errorReporter()
                    .error(location, "Circular include; file includes itself transitively $includeChain")
                error("Circular include: ${location.path} includes itself transitively $includeChain")
            }
            return
        }
        visited[this] = parent

        checkNotNull(includedPrograms == null) { "Included programs already resolved" }

        includedPrograms = thriftIncludes.map { thriftImport ->
            loader.resolveIncludedProgram(location, thriftImport).also {
                it.loadIncludedPrograms(loader, visited, this)
            }
        }

        val symbolMap = mutableMapOf<String, UserType>()
        for (userType in allUserTypes()) {
            symbolMap.putIfAbsent(userType.name, userType)?.also {
                reportDuplicateSymbol(loader.errorReporter(), it, userType)
            }
        }

        val constSymbolMap = mutableMapOf<String, Constant>()
        for (constant in constants) {
            constSymbolMap.putIfAbsent(constant.name, constant)?.also {
                reportDuplicateSymbol(loader.errorReporter(), it, constant)
            }
        }

        constSymbols = constSymbolMap
    }

    private fun reportDuplicateSymbol(
        reporter: ErrorReporter,
        oldValue: UserElement,
        newValue: UserElement,
    ) {
        reporter.error(
            newValue.location,
            "Duplicate symbols: ${oldValue.name} defined at ${oldValue.location} and at ${newValue.location}",
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val that = other as? Program ?: return false

        // Programs are considered equal if they are derived from the same file.
        return location.base == that.location.base && location.path == that.location.path
    }

    override fun hashCode(): Int {
        var result = location.base.hashCode()
        result = 31 * result + location.path.hashCode()
        return result
    }
}
