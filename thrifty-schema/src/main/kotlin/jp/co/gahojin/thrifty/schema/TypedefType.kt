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

import jp.co.gahojin.thrifty.schema.parser.TypeElement
import jp.co.gahojin.thrifty.schema.parser.TypedefElement
import java.util.*

/**
 * Represents a `typedef` alias defined in a .thrift file.
 */
class TypedefType internal constructor(
    mixin: UserElementMixin,
    private val oldTypeElement: TypeElement,
    private var _oldType: ThriftType? = null,
) : UserType(mixin) {

    /**
     * The aliased type
     */
    val oldType: ThriftType
        get() = checkNotNull(_oldType)

    internal constructor(element: TypedefElement, namespaces: Map<NamespaceScope, String>, oldType: ThriftType? = null)
            : this(UserElementMixin(element, namespaces), element.oldType, oldType)

    private constructor(builder: Builder)
            : this(builder.mixin, builder.oldTypeElement, builder.oldType)

    internal fun link(linker: Linker) {
        _oldType = linker.resolveType(oldTypeElement)
    }

    internal fun validate(linker: Linker) {
        val oldType = oldType
        if (oldType.isService) {
            linker.addError(location, "Cannot declare a typedef of a service")
        }

        if (oldType == BuiltinType.VOID) {
            linker.addError(location, "Cannot declare a typedef of void")
        }

        // We've already validated that this is not part of an unresolvable
        // cycle of typedefs (e.g. A -> B -> C -> A) during linking; this
        // happens in Linker#resolveTypedefs().
    }

    override val isTypedef: Boolean = true

    override val trueType: ThriftType
        get() = oldType.trueType

    override fun <T> accept(visitor: Visitor<T>): T = visitor.visitTypedef(this)

    override fun withAnnotations(annotations: Map<String, String>): ThriftType {
        return toBuilder()
            .annotations(mergeAnnotations(this.annotations, annotations))
            .build()
    }

    /**
     * Creates a [Builder] initialized with this type's values.
     */
    fun toBuilder() = Builder(this)

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        val that = other as? TypedefType ?: return false

        return this.oldTypeElement == that.oldTypeElement
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), oldTypeElement)
    }

    /**
     * An object that can create new [TypedefType] instances
     */
    class Builder internal constructor(typedef: TypedefType) : UserTypeBuilder<TypedefType, Builder>(typedef) {
        internal var oldTypeElement: TypeElement = typedef.oldTypeElement
        internal var oldType: ThriftType = typedef.oldType

        /**
         * Use the given [oldTypeElement] for the typedef under construction.
         */
        fun oldTypeElement(oldTypeElement: TypeElement) = apply {
            this.oldTypeElement = oldTypeElement
        }

        /**
         * Use the given [oldType] for the typedef under construction.
         */
        fun oldType(oldType: ThriftType) = apply {
            this.oldType = oldType
        }

        override fun build() = TypedefType(this)
    }
}
