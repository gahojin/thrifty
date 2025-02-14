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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import java.io.Serializable
import java.util.*

/**
 * Thrift options specific to the Kotlin language.
 */
open class KotlinThriftOptions : ThriftOptions(), Serializable {
    enum class ClientStyle {
        NONE,
        DEFAULT,
        COROUTINE,
    }

    override var generateServiceClients: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                if (serviceClientStyle == ClientStyle.NONE) {
                    serviceClientStyle = ClientStyle.DEFAULT
                }
            } else {
                serviceClientStyle = ClientStyle.NONE
            }
        }

    @get:Optional
    @get:Input
    var serviceClientStyle: ClientStyle = ClientStyle.DEFAULT
        set(value) {
            if (field == value) {
                return
            }
            field = value
            generateServiceClients = value != ClientStyle.NONE
        }

    @get:Input
    var isGenerateServer: Boolean = false

    fun setServiceClientStyle(clientStyleName: String) {
        val stylesByName = TreeMap<String, ClientStyle>(String.CASE_INSENSITIVE_ORDER)
        for (style in ClientStyle.entries) {
            stylesByName.put(style.name, style)
        }

        serviceClientStyle = requireNotNull(stylesByName[clientStyleName]) {
            buildString {
                appendLine("Invalid client style; allowed values are:")
                for (value in stylesByName.values) {
                    append("\t- ")
                    appendLine(value.name.lowercase())
                }
            }
        }
    }
}
