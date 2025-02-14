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
 * Thrift options applicable to all supported languages.
 */
abstract class ThriftOptions : Serializable {
    @get:Input
    open var generateServiceClients: Boolean = true

    @get:Optional
    @get:Input
    var nameStyle: FieldNameStyle = FieldNameStyle.DEFAULT

    @get:Optional
    @get:Input
    var listType: String? = null

    @get:Optional
    @get:Input
    var setType: String? = null

    @get:Optional
    @get:Input
    var mapType: String? = null

    @get:Input
    var parcelable: Boolean = false

    @get:Input
    var allowUnknownEnumValues: Boolean = false

    fun setNameStyle(styleName: String) {
        val styles = TreeMap<String, FieldNameStyle>(String.CASE_INSENSITIVE_ORDER)
        for (style in FieldNameStyle.entries) {
            styles.put(style.name, style)
        }

        this.nameStyle = requireNotNull(styles[styleName]) {
            buildString {
                appendLine("Invalid name style; allowed values are:")
                for (value in FieldNameStyle.entries) {
                    append("\t- ")
                    appendLine(value.name.lowercase())
                }
            }
        }
    }
}
