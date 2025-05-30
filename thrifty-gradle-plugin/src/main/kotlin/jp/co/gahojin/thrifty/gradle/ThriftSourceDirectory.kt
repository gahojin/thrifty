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
package jp.co.gahojin.thrifty.gradle

/**
 * Represents a pattern-filterable source directory.
 *
 * Pattern syntax is as in [org.gradle.api.tasks.util.PatternFilterable].
 */
interface ThriftSourceDirectory {
    /**
     * Includes all files described by the given pattern.
     */
    fun include(pattern: String)

    /**
     * Excludes all files described by the given pattern.
     *
     * Exclusions take precedence over inclusions.
     */
    fun exclude(pattern: String)
}
