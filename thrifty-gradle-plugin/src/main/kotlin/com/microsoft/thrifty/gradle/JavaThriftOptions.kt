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
import java.io.Serializable
import java.util.*

/**
 * Thrift options specific to the Java language.
 */
open class JavaThriftOptions : ThriftOptions(), Serializable {
    enum class NullabilityAnnotations(val label: String) {
        NONE("none"),
        ANDROID_SUPPORT("android-support"),
        ANDROIDX("androidx"),
    }

    @get:Input
    var nullabilityAnnotations = NullabilityAnnotations.NONE

    fun setNullabilityAnnotations(nullabilityAnnotations: String) {
        val annotationsByLabel = TreeMap<String, NullabilityAnnotations>(String.CASE_INSENSITIVE_ORDER)
        for (anno in NullabilityAnnotations.entries) {
            annotationsByLabel.put(anno.label, anno)
        }

        val annotations = annotationsByLabel.get(nullabilityAnnotations)
        this.nullabilityAnnotations = requireNotNull(annotations) {
            buildString {
                appendLine("Invalid nullability annotations name; valid values are:")
                for (label in annotationsByLabel.keys) {
                    append("\t- ")
                    appendLine(label)
                }
            }
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
