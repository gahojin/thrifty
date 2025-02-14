/*
 * Thrifty
 *
 * Copyright (c) Microsoft Corporation
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

import org.gradle.api.file.SourceDirectorySet
import javax.inject.Inject

/**
 * The default implementation of [ThriftSourceDirectory].
 * Backed by a [SourceDirectorySet].
 */
open class DefaultThriftSourceDirectory @Inject constructor(val sourceDirectorySet: SourceDirectorySet) : ThriftSourceDirectory {
    private var didClearDefaults = false

    override fun include(pattern: String) {
        clearDefaults()
        sourceDirectorySet.include(pattern)
    }

    override fun exclude(pattern: String) {
        clearDefaults()
        sourceDirectorySet.exclude(pattern)
    }

    private fun clearDefaults() {
        if (didClearDefaults) {
            return
        }

        didClearDefaults = true
        sourceDirectorySet.includes.clear()
        sourceDirectorySet.excludes.clear()
    }
}
