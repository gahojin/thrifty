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

import jp.co.gahojin.thrifty.compiler.TypeProcessorService.javaProcessor
import jp.co.gahojin.thrifty.compiler.TypeProcessorService.kotlinProcessor
import jp.co.gahojin.thrifty.gen.NullabilityAnnotationType
import jp.co.gahojin.thrifty.gen.ThriftyCodeGenerator
import jp.co.gahojin.thrifty.gradle.JavaThriftOptions.NullabilityAnnotations
import jp.co.gahojin.thrifty.gradle.KotlinThriftOptions.ClientStyle
import jp.co.gahojin.thrifty.kgen.KotlinCodeGenerator
import jp.co.gahojin.thrifty.schema.ErrorReporter
import jp.co.gahojin.thrifty.schema.FieldNamingPolicy
import jp.co.gahojin.thrifty.schema.LoadFailedException
import jp.co.gahojin.thrifty.schema.Loader
import jp.co.gahojin.thrifty.schema.Schema
import org.gradle.api.GradleException
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.workers.WorkAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * A [WorkAction] that actually generates the Thrifty sources.
 *
 * We're doing this via the Worker API to ensure that Gradle's hard-coded Kotlin
 * version doesn't cause us grief.  Thrifty is entirely written in Kotlin, and
 * there's no guarantee that we'll be using a version compatible with whatever
 * Gradle happens to have bundled.  According to some of their engineers, this
 * (with classpath-level isolation) is the only safe way to use Kotlin in the context
 * of a Gradle plugin.
 */
abstract class GenerateThriftSourcesWorkAction : WorkAction<GenerateThriftSourcesWorkParams> {
    override fun execute() {
        try {
            actuallyExecute()
        } catch (e: IOException) {
            throw GradleException("Thrift generation failed", e)
        }
    }

    private fun actuallyExecute() {
        val schema: Schema?
        try {
            val loader = Loader()
            for (file in parameters.includePath.get()) {
                loader.addIncludePath(file.toPath())
            }

            for (file in parameters.source) {
                loader.addThriftFile(file.toPath())
            }

            schema = loader.load()
        } catch (e: LoadFailedException) {
            reportThriftException(e)
            throw GradleException("Thrift compilation failed", e)
        }

        try {
            deleteRecursively(parameters.outputDirectory.get().asFile)
        } catch (e: IOException) {
            LOGGER.warn("Error clearing stale output", e)
        }

        val opts = parameters.thriftOptions.get()
        if (opts.isKotlin) {
            generateKotlinThrifts(schema, opts)
        } else if (opts.isJava) {
            generateJavaThrifts(schema, opts)
        } else {
            error("Only Java or Kotlin thrift options are supported")
        }
    }

    private fun reportThriftException(e: LoadFailedException) {
        for (report in e.errorReporter.reports) {
            val template = "{}: {}"
            when (report.level) {
                ErrorReporter.Level.WARNING -> LOGGER.warn(template, report.location, report.message)
                ErrorReporter.Level.ERROR -> LOGGER.error(template, report.location, report.message)
            }
        }

        val sst = parameters.showStacktrace.orNull ?: ShowStacktrace.INTERNAL_EXCEPTIONS
        when (sst) {
            ShowStacktrace.ALWAYS, ShowStacktrace.ALWAYS_FULL -> LOGGER.error("Thrift compilation failed", e)
            ShowStacktrace.INTERNAL_EXCEPTIONS -> Unit
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun deleteRecursively(file: File) {
        file.toPath().deleteRecursively()
    }

    private fun generateKotlinThrifts(schema: Schema, opts: SerializableThriftOptions) {
        val gen = KotlinCodeGenerator(policyFromNameStyle(opts.nameStyle))
            .emitJvmName()
            .filePerType()
            .failOnUnknownEnumValues(!opts.isAllowUnknownEnumValues)
            .mutableFields(opts.isMutableFields)

        if (opts.isParcelable) {
            gen.parcelize()
        }

        val kopt = checkNotNull(opts.kotlinOpts)

        if (opts.isGenerateServiceClients) {
            when (kopt.serviceClientStyle) {
                ClientStyle.DEFAULT -> Unit
                ClientStyle.NONE -> gen.omitServiceClients()
            }
        } else {
            gen.omitServiceClients()
        }

        if (kopt.isGenerateServer) {
            gen.generateServer()
        }

        if (kopt.jvmName) {
            gen.emitJvmName()
        }

        if (kopt.jvmStatic) {
            gen.emitJvmStatic()
        }

        if (kopt.jvmOverloads) {
            gen.emitJvmOverloads()
        }

        if (kopt.bigEnum) {
            gen.emitBigEnums()
        }

        if (opts.listType != null) {
            gen.listClassName(opts.listType)
        }

        if (opts.setType != null) {
            gen.setClassName(opts.setType)
        }

        if (opts.mapType != null) {
            gen.mapClassName(opts.mapType)
        }

        val kotlinProcessor = kotlinProcessor
        if (kotlinProcessor != null) {
            gen.processor = kotlinProcessor
        }

        for (fs in gen.generate(schema)) {
            fs.writeTo(parameters.outputDirectory.asFile.get())
        }
    }

    private fun generateJavaThrifts(schema: Schema, opts: SerializableThriftOptions) {
        val gen = ThriftyCodeGenerator(schema, policyFromNameStyle(opts.nameStyle))
        gen.emitFileComment(true)
        gen.emitParcelable(opts.isParcelable)
        gen.failOnUnknownEnumValues(!opts.isAllowUnknownEnumValues)

        if (opts.listType != null) {
            gen.withListType(opts.listType)
        }

        if (opts.setType != null) {
            gen.withSetType(opts.setType)
        }

        if (opts.mapType != null) {
            gen.withMapType(opts.mapType)
        }

        val jopt = checkNotNull(opts.javaOpts)

        when (jopt.nullabilityAnnotations) {
            NullabilityAnnotations.NONE -> gen.nullabilityAnnotationType(NullabilityAnnotationType.NONE)
            NullabilityAnnotations.JETBRAINS -> gen.nullabilityAnnotationType(NullabilityAnnotationType.JETBRAINS)
            NullabilityAnnotations.ANDROIDX -> gen.nullabilityAnnotationType(NullabilityAnnotationType.ANDROIDX)
        }

        val typeProcessor = javaProcessor
        if (typeProcessor != null) {
            gen.usingTypeProcessor(typeProcessor)
        }

        gen.generate(parameters.outputDirectory.asFile.get())
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(GenerateThriftSourcesWorkAction::class.java)

        private fun policyFromNameStyle(style: FieldNameStyle): FieldNamingPolicy {
            return when (style) {
                FieldNameStyle.DEFAULT -> FieldNamingPolicy.DEFAULT
                FieldNameStyle.JAVA -> FieldNamingPolicy.JAVA
                FieldNameStyle.PASCAL -> FieldNamingPolicy.PASCAL
            }
        }
    }
}
