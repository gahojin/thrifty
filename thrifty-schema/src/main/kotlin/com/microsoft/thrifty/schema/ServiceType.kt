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
package com.microsoft.thrifty.schema

import com.microsoft.thrifty.schema.parser.ServiceElement
import com.microsoft.thrifty.schema.parser.TypeElement
import java.util.*

/**
 * Represents a `service` defined in a .thrift file.
 */
class ServiceType : UserType {
    /** The methods defined by this service. */
    val methods: List<ServiceMethod>
    private val extendsServiceType: TypeElement?

    /**
     * The type of the service that this service extends, or null if this does
     * not extend any other service.
     */
    // This is intentionally too broad - it is not legal for a service to extend
    // a non-service type, but if we've parsed that we need to keep the invalid
    // state long enough to catch it during link validation.
    var extendsService: ThriftType? = null
        private set

    internal constructor(element: ServiceElement, namespaces: Map<NamespaceScope, String>) : super(
        mixin = UserElementMixin(element, namespaces),
    ) {
        this.extendsServiceType = element.extendsService
        this.methods = element.functions.map { ServiceMethod(it, namespaces) }
    }

    private constructor(builder: Builder) : super(builder.mixin) {
        this.methods = builder.methods
        this.extendsServiceType = builder.extendsServiceType
        this.extendsService = builder.extendsService
    }

    override val isService: Boolean = true

    override fun <T> accept(visitor: Visitor<T>): T = visitor.visitService(this)

    override fun withAnnotations(annotations: Map<String, String>): ThriftType {
        return toBuilder()
            .annotations(mergeAnnotations(this.annotations, annotations))
            .build()
    }

    /**
     * Creates a [Builder] initialized with this service's values.
     */
    fun toBuilder() = Builder(this)

    internal fun link(linker: Linker) {
        for (method in methods) {
            method.link(linker)
        }

        if (this.extendsServiceType != null) {
            this.extendsService = linker.resolveType(extendsServiceType)
        }
    }

    internal fun validate(linker: Linker) {
        // Validate the following properties:
        // 1. If the service extends a type, that the type is itself a service
        // 2. The service contains no duplicate methods, including those inherited from base types.
        // 3. All service methods themselves are valid.

        val methodsByName = linkedMapOf<String, ServiceMethod>()

        val hierarchy = ArrayDeque<ServiceType>()

        extendsService?.also {
            if (!it.isService) {
                linker.addError(location, "Base type '${it.name}' is not a service")
            }
        }

        // Assume base services have already been validated
        var baseType = extendsService
        while (baseType != null) {
            if (!baseType.isService) {
                break
            }

            val svc = baseType as ServiceType
            hierarchy.add(svc)

            baseType = svc.extendsService
        }

        while (!hierarchy.isEmpty()) {
            // Process from most- to least-derived services; that way, if there
            // is a name conflict, we'll report the conflict with the least-derived
            // class.
            val svc = hierarchy.remove()

            for (serviceMethod in svc.methods) {
                // Add the base-type method names to the map.  In this case,
                // we don't care about duplicates because the base types have
                // already been validated and we have already reported that error.
                methodsByName[serviceMethod.name] = serviceMethod
            }
        }

        for (method in methods) {
            methodsByName.putIfAbsent(method.name, method)?.also {
                linker.addError(
                    method.location,
                    "Duplicate method; '${method.name}' conflicts with another method declared at ${it.location}",
                )
            }
        }

        for (method in methods) {
            method.validate(linker)
        }
    }

    /**
     * An object that can create new [ServiceType] instances.
     */
    class Builder internal constructor(type: ServiceType) : UserTypeBuilder<ServiceType, Builder>(type) {
        internal var methods: List<ServiceMethod> = type.methods
        internal val extendsServiceType: TypeElement? = type.extendsServiceType
        internal var extendsService: ThriftType? = type.extendsService

        /**
         * Use the given [methods] for the service under construction.
         */
        fun methods(methods: List<ServiceMethod>) = apply {
            this.methods = methods
        }

        /**
         * Use the given [base type][extendsService] for the service under
         * construction.
         */
        fun extendsService(extendsService: ThriftType?) = apply {
            this.extendsService = extendsService
        }

        /**
         * Creates a new [ServiceType] instance.
         */
        override fun build() = ServiceType(this)
    }
}
