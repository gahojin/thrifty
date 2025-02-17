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
package com.microsoft.thrifty.gradle

import com.microsoft.thrifty.gradle.JavaThriftOptions.NullabilityAnnotations
import com.microsoft.thrifty.gradle.KotlinThriftOptions.ClientStyle
import java.io.Serializable

// Can't just use ThriftOptions cuz Gradle decorates them with non-serializable types,
// and we need to pass these options to Worker API params that must be serializable.
class SerializableThriftOptions @JvmOverloads constructor(
    val isGenerateServiceClients: Boolean = true,
    val nameStyle: FieldNameStyle = FieldNameStyle.DEFAULT,
    val listType: String? = null,
    val setType: String? = null,
    val mapType: String? = null,
    val isParcelable: Boolean = false,
    val isAllowUnknownEnumValues: Boolean = false,
    val kotlinOpts: Kotlin? = null,
    val javaOpts: Java? = null,
) : Serializable {
    class Kotlin @JvmOverloads constructor(
        val serviceClientStyle: ClientStyle = ClientStyle.DEFAULT,
        val isGenerateServer: Boolean = false,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    class Java @JvmOverloads constructor(
        val nullabilityAnnotations: NullabilityAnnotations = NullabilityAnnotations.NONE,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    constructor(options: ThriftOptions) : this(
        isGenerateServiceClients = options.generateServiceClients,
        nameStyle = options.nameStyle,
        listType = options.listType,
        setType = options.setType,
        mapType = options.mapType,
        isParcelable = options.parcelable,
        isAllowUnknownEnumValues = options.allowUnknownEnumValues,
        kotlinOpts = (options as? KotlinThriftOptions)?.let {
            Kotlin(it.serviceClientStyle, it.isGenerateServer)
        },
        javaOpts = (options as? JavaThriftOptions)?.let {
            Java(it.nullabilityAnnotations)
        },
    )

    val isJava: Boolean
        get() = javaOpts != null

    val isKotlin: Boolean
        get() = kotlinOpts != null

    companion object {
        private const val serialVersionUID = 1L
    }
}
