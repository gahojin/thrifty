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

import jp.co.gahojin.thrifty.schema.parser.FieldElement
import jp.co.gahojin.thrifty.schema.parser.FunctionElement
import jp.co.gahojin.thrifty.schema.parser.StructElement

/**
 * Represents a method defined by a Thrift service.
 *
 * @property parameters The parameters accepted by this method.
 * @property exceptions The exceptions thrown by this method.
 */
class ServiceMethod private constructor(
    private val element: FunctionElement,
    private val mixin: UserElementMixin,
    val parameters: List<Field> = element.params.map { Field(it, mixin.namespaces) },
    val exceptions: List<Field> = element.exceptions.map { Field(it, mixin.namespaces) },
    private var _returnType: ThriftType? = null,
) : UserElement by mixin {
    val argsStruct = StructType(
        element = StructElement(
            location = element.location,
            name = FieldNamingPolicy.PASCAL.apply("${element.name}_Args"),
            type = StructElement.Type.STRUCT,
            fields = element.params,
        ),
        namespaces = mixin.namespaces,
    )

    val resultStruct = StructType(
        element = StructElement(
            location = element.location,
            name = FieldNamingPolicy.PASCAL.apply("${element.name}_Result"),
            type = StructElement.Type.UNION,
            fields = element.exceptions + if (element.returnType.name == BuiltinType.VOID.name) emptyList() else listOf(
                FieldElement(
                    location = element.location,
                    fieldId = 0,
                    type = element.returnType,
                    name = "success",
                ),
            ),
        ),
        namespaces = mixin.namespaces,
    )

    /**
     * The type of value returned by this method, or [BuiltinType.VOID].
     */
    val returnType: ThriftType
        get() = checkNotNull(_returnType)

    /**
     * True if this method was declared as `oneway`, otherwise false.
     */
    val oneWay: Boolean
        get() = element.oneWay

    internal constructor(element: FunctionElement, namespaces: Map<NamespaceScope, String>) : this(
        element = element,
        mixin = UserElementMixin(element, namespaces),
    )

    /**
     * Creates a new [Builder] initialized with this method's values.
     */
    fun toBuilder() = Builder(this)

    internal fun link(linker: Linker) {
        for (parameter in parameters) {
            parameter.link(linker)
        }

        for (exception in exceptions) {
            exception.link(linker)
        }

        _returnType = linker.resolveType(element.returnType)
        argsStruct.link(linker)
        resultStruct.link(linker)
    }

    internal fun validate(linker: Linker) {
        if (oneWay && BuiltinType.VOID != returnType) {
            linker.addError(location, "oneway methods may not have a non-void return type")
        }

        if (oneWay && !exceptions.isEmpty()) {
            linker.addError(location, "oneway methods may not throw exceptions")
        }

        val fieldsById = linkedMapOf<Int, Field>()
        for (param in parameters) {
            fieldsById.putIfAbsent(param.id, param)?.also {
                val fmt = "Duplicate parameters; param '%s' has the same ID (%s) as param '%s'"
                linker.addError(param.location, String.format(fmt, param.name, param.id, it.name))
            }
        }

        fieldsById.clear()
        for (exn in exceptions) {
            fieldsById.putIfAbsent(exn.id, exn)?.also {
                val fmt = "Duplicate exceptions; exception '%s' has the same ID (%s) as exception '%s'"
                linker.addError(exn.location, String.format(fmt, exn.name, exn.id, it.name))
            }
        }

        for (field in exceptions) {
            val type = field.type
            if (type.isStruct) {
                val struct = type as StructType
                if (struct.isException) {
                    continue
                }
            }

            linker.addError(field.location, "Only exception types can be thrown")
        }
    }

    /**
     * An object that can create new [ServiceMethod] instances.
     */
    class Builder internal constructor(
        method: ServiceMethod,
    ) : AbstractUserElementBuilder<ServiceMethod, Builder>(method.mixin) {

        private val element: FunctionElement = method.element
        private var parameters: List<Field> = method.parameters
        private var exceptions: List<Field> = method.exceptions
        private var returnType: ThriftType? = method.returnType

        /**
         * Use the given [parameters] for the method under construction.
         */
        fun parameters(parameters: List<Field>) = apply {
            this.parameters = parameters.toList()
        }

        /**
         * Use the given [exceptions] for the method under construction.
         */
        fun exceptions(exceptions: List<Field>) = apply {
            this.exceptions = exceptions.toList()
        }

        /**
         * Use the given return [type] for the method under construction.
         */
        fun returnType(type: ThriftType) = apply {
            returnType = type
        }

        /**
         * Creates a new [ServiceMethod] instance.
         */
        override fun build() = ServiceMethod(element, mixin, parameters, exceptions, returnType)
    }
}
