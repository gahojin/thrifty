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
 * Represents a Thrift `set<T>`.
 *
 * @property elementType The type of value contained by instances of this type.
 */
class SetType internal constructor(
    val elementType: ThriftType,
    override val annotations: Map<String, String> = emptyMap(),
) : ThriftType("set<${elementType.name}>") {

    override val isSet: Boolean = true

    override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSet(this)

    override fun withAnnotations(annotations: Map<String, String>): ThriftType {
        return SetType(elementType, mergeAnnotations(this.annotations, annotations))
    }

    /**
     * Creates a [Builder] initialized with this type's values.
     */
    fun toBuilder() = Builder(this)

    /**
     * An object that can create new [SetType] instances.
     */
    class Builder(
        private var elementType: ThriftType,
        private var annotations: Map<String, String>,
    ) {
        internal constructor(type: SetType) : this(type, type.annotations)

        /**
         * Use the given [elementType] with the set type under construction.
         */
        fun elementType(elementType: ThriftType) = apply {
            this.elementType = elementType
        }

        /**
         * Use the given [annotations] with the set type under construction.
         */
        fun annotations(annotations: Map<String, String>) = apply {
            this.annotations = annotations
        }

        /**
         * Creates a new [SetType] instance.
         */
        fun build() = SetType(elementType, annotations)
    }
}
