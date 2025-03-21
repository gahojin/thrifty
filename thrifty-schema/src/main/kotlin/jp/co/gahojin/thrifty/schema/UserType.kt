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

import java.util.*

/**
 * Base type of all user-defined Thrift IDL types, including structs, unions,
 * exceptions, services, and typedefs.
 */
abstract class UserType internal constructor(
    private val mixin: UserElementMixin,
) : ThriftType(mixin.name), UserElement by mixin {

    override val isDeprecated: Boolean
        get() = mixin.isDeprecated

    override val name: String = mixin.name

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        val that = other as? UserType ?: return false

        return this.mixin == that.mixin
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), mixin)
    }

    /**
     * A base type for builders of all UserType-derived types.
     */
    abstract class UserTypeBuilder<
        TType : UserType,
        TBuilder : UserTypeBuilder<TType, TBuilder>,
    > internal constructor(type: TType) : AbstractUserElementBuilder<TType, TBuilder>(type.mixin)
}
