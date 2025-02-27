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

/**
 * Encapsulates all types, values, and services defined in a set of Thrift
 * files.
 *
 * Strictly speaking, this is a lossy representation - the original filesystem
 * structure of the source Programs is not preserved.  As such, this isn't a
 * suitable representation from which to generate C++ code, or any other
 * module-based language for the matter.  But as we're only concerned with Java
 * here, it's perfectly convenient.
 */
class Schema {
    /**
     * All `struct` entities contained in the parsed .thrift files
     */
    val structs: List<StructType>

    /**
     * All `union` entities contained in the parsed .thrift files
     */
    val unions: List<StructType>

    /**
     * All `exception` entities contained in the parsed .thrift files
     */
    val exceptions: List<StructType>

    /**
     * All `enum` types defined in the parsed .thrift files.
     */
    val enums: List<EnumType>

    /**
     * All `const` elements defined in the parsed .thrift files.
     */
    val constants: List<Constant>

    /**
     * All `typedef` aliases defined in the parsed .thrift files.
     */
    val typedefs: List<TypedefType>

    /**
     * All `service` types defined in the parsed .thrift files.
     */
    val services: List<ServiceType>

    /**
     * @return an [Iterable] of all [UserElements][UserElement] in this [Schema].
     */
    fun elements(): Iterable<UserElement> {
        return structs + unions + exceptions + enums + constants + typedefs + services
    }

    internal constructor(programs: Iterable<Program>) {
        val structs = mutableListOf<StructType>()
        val unions = mutableListOf<StructType>()
        val exceptions = mutableListOf<StructType>()
        val enums = mutableListOf<EnumType>()
        val constants = mutableListOf<Constant>()
        val typedefs = mutableListOf<TypedefType>()
        val services = mutableListOf<ServiceType>()

        for (program in programs) {
            structs.addAll(program.structs)
            unions.addAll(program.unions)
            exceptions.addAll(program.exceptions)
            enums.addAll(program.enums)
            constants.addAll(program.constants)
            typedefs.addAll(program.typedefs)
            services.addAll(program.services)
        }

        this.structs = structs
        this.unions = unions
        this.exceptions = exceptions
        this.enums = enums
        this.constants = sortConstantsInDependencyOrder(constants)
        this.typedefs = typedefs
        this.services = services
    }

    private constructor(builder: Builder) {
        this.structs = builder.structs
        this.unions = builder.unions
        this.exceptions = builder.exceptions
        this.enums = builder.enums
        this.constants = sortConstantsInDependencyOrder(builder.constants)
        this.typedefs = builder.typedefs
        this.services = builder.services
    }

    /**
     * Returns a [Builder] initialized with this schema's types.
     */
    fun toBuilder() = Builder(structs, unions, exceptions, enums, constants, typedefs, services)

    /**
     * A builder for [schemas][Schema].
     */
    class Builder internal constructor(
        internal var structs: List<StructType>,
        internal var unions: List<StructType>,
        internal var exceptions: List<StructType>,
        internal var enums: List<EnumType>,
        internal var constants: List<Constant>,
        internal var typedefs: List<TypedefType>,
        internal var services: List<ServiceType>,
    ) {
        /**
         * Use the given [structs] for the schema under construction.
         */
        fun structs(structs: List<StructType>) = apply {
            this.structs = structs.toList()
        }

        /**
         * Use the given [unions] for the schema under construction.
         */
        fun unions(unions: List<StructType>) = apply {
            this.unions = unions.toList()
        }

        /**
         * Use the given [exceptions] for the schema under construction.
         */
        fun exceptions(exceptions: List<StructType>) = apply {
            this.exceptions = exceptions.toList()
        }

        /**
         * Use the given [enums] for the schema under construction.
         */
        fun enums(enums: List<EnumType>) = apply {
            this.enums = enums.toList()
        }

        /**
         * Use the given [constants] for the schema under construction.
         */
        fun constants(constants: List<Constant>) = apply {
            this.constants = constants.toList()
        }

        /**
         * Use the given [typedefs] for the schema under construction.
         */
        fun typedefs(typedefs: List<TypedefType>) = apply {
            this.typedefs = typedefs.toList()
        }

        /**
         * Use the given [services] for the schema under construction.
         */
        fun services(services: List<ServiceType>) = apply {
            this.services = services.toList()
        }

        /**
         * Build a new [Schema].
         */
        fun build() = Schema(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        val that = other as? Schema ?: return false
        if (structs != that.structs) return false
        if (unions != that.unions) return false
        if (exceptions != that.exceptions) return false
        if (enums != that.enums) return false
        if (constants != that.constants) return false
        if (typedefs != that.typedefs) return false
        if (services != that.services) return false
        return true
    }

    override fun hashCode(): Int {
        var result = structs.hashCode()
        result = 31 * result + unions.hashCode()
        result = 31 * result + exceptions.hashCode()
        result = 31 * result + enums.hashCode()
        result = 31 * result + constants.hashCode()
        result = 31 * result + typedefs.hashCode()
        result = 31 * result + services.hashCode()
        return result
    }

    /**
     * Returns a copy of the collection of [Constants][Constant] sorted in dependency
     * order, suitable for generating code in the order presented.
     *
     * Dependency-order is a reverse topological sort, and  here means that for any
     * given constant in the list, all constants on which it depends (i.e. that it
     * references) come before it in order.  For example:
     *
     * ```
     * // given this thrift
     * const list<string> STRS = [A, B];
     * const string A = "a";
     * const string B = "b";
     *
     * // A reverse-topological-sort of these constants would be:
     * [A, B, STRS]
     *
     * // A naive code-generation strategy of emitting constants in the order
     * // in which they appear will work as expected:
     * val A: String = "a"
     * val B: String = "b"
     * val STRS: List<String> = listOf(A, B)
     * ```
     */
    private fun sortConstantsInDependencyOrder(constants: List<Constant>): List<Constant> {
        return SortUtil.inDependencyOrder(constants) { it.referencedConstants }
    }
}
