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
package com.microsoft.thrifty.compiler

import com.microsoft.thrifty.compiler.spi.KotlinTypeProcessor
import com.microsoft.thrifty.compiler.spi.TypeProcessor
import java.util.*

/**
 * An object that locate [TypeProcessor] and [KotlinTypeProcessor]
 * objects from the current classpath.
 *
 * Used by the compiler to detect and run user-provided processors.
 */
object TypeProcessorService {
    private val serviceLoader: ServiceLoader<TypeProcessor> = ServiceLoader.load(TypeProcessor::class.java)
    private val kotlinProcessorLoader: ServiceLoader<KotlinTypeProcessor> = ServiceLoader.load(KotlinTypeProcessor::class.java)

    /**
     * Gets the first [TypeProcessor] implementation loaded, or `null` if none are found.
     *
     * Because service ordering is non-deterministic, only the first instance
     * is returned.  A warning will be printed if more than one are found.
     */
    val javaProcessor by lazy { loadSingleProcessor(serviceLoader) }

    /**
     * Gets the first [KotlinTypeProcessor] implementation loaded, or `null` if none are found.
     *
     * Because service ordering is non-deterministic, only the first instance
     * is returned.  A warning will be printed if more than one are found.
     */
    val kotlinProcessor by lazy { loadSingleProcessor(kotlinProcessorLoader) }

    private fun <T : Any> loadSingleProcessor(serviceLoader: ServiceLoader<T>): T? {
        var processor: T? = null

        val iter = serviceLoader.iterator()
        if (iter.hasNext()) {
            processor = iter.next()

            if (iter.hasNext()) {
                System.err.println("Multiple processors found; using ${processor.javaClass.getName()}")
            }
        }

        return processor
    }
}
