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
package jp.co.gahojin.thrifty.compiler.spi

import com.squareup.kotlinpoet.TypeSpec

/**
 * When specified as part of code generation, processes all types after they
 * are computed, but before they are written to disk.  This allows you to make
 * arbitrary modifications to types such as implementing your own interfaces,
 * renaming fields, or anything you might wish to do.
 *
 * For example, a processor that implements java.lang.Serializable on all
 * generated types:
 *
 * ```kotlin
 * class SerializableTypeProcessor : KotlinTypeProcessor {
 *   override fun process(spec: TypeSpec): TypeSpec? {
 *     return type.toBuilder().let {
 *       it.addSuperinterface(Serializable::class)
 *       val companion = TypeSpec.companionBuilder()
 *           .addProperty(PropertySpec.builder("serialVersionUID", Long::class)
 *               .addModifiers(KModifier.PRIVATE)
 *               .jvmField()
 *               .initializer("-1")
 *               .build())
 *           .build()
 *       it.addType(companion)
 *       it.build()
 *     }
 *   }
 * }
 * ```
 */
fun interface KotlinTypeProcessor {
    /**
     * Processes and returns a given type.
     *
     * The given `type` will have been generated from compiled Thrift
     * files, and will not have been written to disk.  It can be returned
     * unaltered, or a modified copy can be returned.
     *
     * Finally, if `null` is returned, then no file will be generated.
     * This can be used to selectively suppress types, e.g. if it is known that
     * it will be unused.
     *
     * @param type a [TypeSpec] generated based on Thrift IDL.
     * @return a (possibly modified) [TypeSpec] to be written to disk, or null.
     */
    fun process(type: TypeSpec): TypeSpec?
}
