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
package jp.co.gahojin.thrifty.compiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import jp.co.gahojin.thrifty.gen.NullabilityAnnotationType
import jp.co.gahojin.thrifty.gen.ThriftyCodeGenerator
import jp.co.gahojin.thrifty.kgen.KotlinCodeGenerator
import jp.co.gahojin.thrifty.schema.FieldNamingPolicy
import jp.co.gahojin.thrifty.schema.LoadFailedException
import jp.co.gahojin.thrifty.schema.Loader
import jp.co.gahojin.thrifty.schema.Schema
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * A program that compiles Thrift IDL files into Java source code for use
 * with thrifty-runtime.
 *
 * ```
 * java -jar thrifty-compiler.jar --out=/path/to/output
 * [--path=dir/for/search/path]
 * [--list-type=java.util.ArrayList]
 * [--set-type=java.util.HashSet]
 * [--map-type=java.util.HashMap]
 * [--lang=[java|kotlin]]
 * [--kt-file-per-type]
 * [--kt-jvm-static]
 * [--kt-big-enums]
 * [--parcelable]
 * [--nullability-annotation-type=[none|jetbrains|androidx]]
 * [--omit-service-clients]
 * [--omit-file-comments]
 * file1.thrift
 * file2.thrift
 * ...
 * ```
 *
 * `--out` is required, and specifies the directory to which generated
 * Java sources will be written.
 *
 * `--path` can be given multiple times.  Each directory so specified
 * will be placed on the search path.  When resolving `include` statements
 * during thrift compilation, these directories will be searched for included files.
 *
 * `--list-type` is optional.  When provided, the compiler will use the given
 * class name when instantiating list-typed values.  Defaults to [ArrayList].
 *
 * `--set-type` is optional.  When provided, the compiler will use the given
 * class name when instantiating set-typed values.  Defaults to [java.util.HashSet].
 *
 * `--map-type` is optional.  When provided, the compiler will use the given
 * class name when instantiating map-typed values.  Defaults to [java.util.HashMap].
 * Android users will likely wish to substitute `android.support.v4.util.ArrayMap`.
 *
 * `--lang=[java|kotlin]` is optional, defaulting to Kotlin.  When provided, the
 * compiler will generate code in the specified language.
 *
 * `--kt-file-per-type` is optional.  When specified, one Kotlin file will be generated
 * for each top-level generated Thrift type.  When absent (the default), all generated
 * types in a single package will go in one file named `ThriftTypes.kt`.  Implies
 * `--lang=kotlin`.
 *
 * `--kt-jvm-static` is optional.  When specified, certain companion-object functions will
 * be annotated with [JvmStatic].  This option is for those who want easier Java interop,
 * and results in slightly larger code.  Implies `--lang=kotlin`.
 *
 * `--kt-big-enums` is optional.  When specified, generated enums will use a different
 * representation.  Rather than each enum member containing its value, a single large
 * function mapping enums to values will be generated.  This works around some JVM class-size
 * limitations in some extreme cases, such as an enum with thousands of members.  This should
 * be avoided unless you know you need it.  Implies `--lang=kotlin`.
 *
 * `--parcelable` is optional.  When provided, generated types will contain a
 * `Parcelable` implementation.  Kotlin types will use the `@Parcelize` extension.
 *
 * `--nullability-annotation-type=[none|jetbrains|androidx]` is optional, defaulting to
 * `none`.  When specified as something other than `none`, generated Java classes will have
 * `@Nullable` or `@NotNull` annotations, as appropriate.
 * Use the `jetbrain` option for projects that are using the JetBrains Annotation Library.
 * Use the `androidx` option for projects that have migrated to AndroidX.
 * Has no effect on Kotlin code.  This flag implies '--lang=java'.
 *
 * `--omit-service-clients` is optional.  When specified, no service clients are generated.
 *
 * `--omit-file-comments` is optional.  When specified, no file-header comment is generated.
 * The default behavior is to prefix generated files with a comment indicating that they
 * are generated by Thrifty, and should probably not be modified by hand.
 *
 * If no .thrift files are given, then all .thrift files located on the search path
 * will be implicitly included; otherwise only the given files (and those included by them)
 * will be compiled.
 */
object ThriftyCompiler {
    enum class Language {
        JAVA,
        KOTLIN,
    }

    private val cli = object : CliktCommand(name = "thrifty-compiler") {
        val outputDirectory: Path by option("-o", "--out")
            .help("the output directory for generated files")
            .path(canBeFile = false, canBeDir = true)
            .required()
            .validate { Files.isDirectory(it) || !Files.exists(it) }

        val searchPath: List<Path> by option("-p", "--path")
            .help("the search path for .thrift includes")
            .path(mustExist = true, canBeDir = true, canBeFile = false)
            .multiple()

        val language: Language? by option("-l", "--lang")
            .help("the target language for generated code.  Default is kotlin.")
            .choice("java" to Language.JAVA, "kotlin" to Language.KOTLIN)

        val nameStyle: FieldNamingPolicy by option("--name-style")
            .help("Format style for generated names.  Default is to leave names unaltered.")
            .choice("default" to FieldNamingPolicy.DEFAULT, "java" to FieldNamingPolicy.JAVA)
            .default(FieldNamingPolicy.DEFAULT)

        val listTypeName: String? by option("--list-type")
            .help("when specified, the concrete type to use for lists")
        val setTypeName: String? by option("--set-type")
            .help("when specified, the concrete type to use for sets")
        val mapTypeName: String? by option("--map-type")
            .help("when specified, the concrete type to use for maps")

        val nullabilityAnnotationType: NullabilityAnnotationType by option("--nullability-annotation-type")
            .help("the type of nullability annotations, if any, to add to fields.  Default is none.  Implies --lang=java.")
            .choice(
                "none" to NullabilityAnnotationType.NONE,
                "jetbrains" to NullabilityAnnotationType.JETBRAINS,
                "androidx" to NullabilityAnnotationType.ANDROIDX,
            )
            .transformAll {
                it.lastOrNull() ?: NullabilityAnnotationType.NONE
            }

        val emitParcelable: Boolean by option("--parcelable")
            .help("When set, generates Parcelable implementations for structs")
            .flag(default = false)

        val omitServiceClients: Boolean by option("--omit-service-clients")
            .help("When set, don't generate service clients")
            .flag(default = false)

        val omitStructImplements: Boolean by option("--omit-struct-implements")
            .help("When set, don't generate struct implements")
            .flag(default = false)

        val generateServer: Boolean by option("--experimental-kt-generate-server")
            .help("When set, generate kotlin server implementation (EXPERIMENTAL)")
            .flag(default = false)

        val omitFileComments: Boolean by option("--omit-file-comments")
            .help("When set, don't add file comments to generated files")
            .flag(default = false)

        val kotlinFilePerType: Boolean by option("--kt-file-per-type")
            .help("Generate one .kt file per type; default is one per namespace.")
            .flag(default = false)

        val kotlinJvmName: Boolean by option("--kt-emit-jvmname")
            .help("When set, emit @JvmName annotations")
            .flag(default = false)

        val kotlinJvmStatic: Boolean by option("--kt-jvm-static")
            .help("Add @JvmStatic annotations to companion-object functions.  For ease-of-use with Java code.")
            .flag("--kt-no-jvm-static", default = false)

        val kotlinJvmOverloads: Boolean by option("--kt-jvm-overloads")
            .help("When set, emit @JvmOverloads annotations")
            .flag("--kt-no-jvm-overloads", default = false)

        val kotlinBigEnums: Boolean by option("--kt-big-enums")
            .flag("--kt-no-big-enums", default = false)

        val thriftFiles: List<Path> by argument()
            .help("All .thrift files to compile")
            .path(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)
            .multiple()

        val failOnUnknownEnumValues by option("--fail-on-unknown-enum-values")
            .help("When set, unknown values found when decoding will throw an exception. Otherwise, it uses null/default values.")
            .flag("--no-fail-on-unknown-enum-values", default = true)

        val mutableFields by option("--mutable-fields")
            .help("When set, Field is set to Mutable.")
            .flag("--no-mutable-fields", default = false)

        override fun help(context: Context) = "Generate Java or Kotlin code from .thrift files"

        override fun run() {
            val loader = Loader()
            for (thriftFile in thriftFiles) {
                loader.addThriftFile(thriftFile)
            }

            loader.addIncludePath(Paths.get(System.getProperty("user.dir")))
            for (dir in searchPath) {
                loader.addIncludePath(dir)
            }

            val schema: Schema
            try {
                schema = loader.load()
            } catch (e: LoadFailedException) {
                if (!e.errorReporter.hasError && e.cause != null) {
                    println(e.cause)
                }
                for (report in e.errorReporter.formattedReports()) {
                    println(report)
                }
                exitProcess(1)
            }

            val impliedLanguage = when {
                kotlinFilePerType -> Language.KOTLIN
                kotlinJvmName -> Language.KOTLIN
                kotlinJvmStatic -> Language.KOTLIN
                kotlinJvmOverloads -> Language.KOTLIN
                kotlinBigEnums -> Language.KOTLIN
                nullabilityAnnotationType != NullabilityAnnotationType.NONE -> Language.JAVA
                else -> null
            }

            if (language != null && impliedLanguage != null && impliedLanguage != language) {
                echo(
                    "You specified $language, but provided options implying $impliedLanguage (which will be ignored).",
                    err = true,
                )
            }

            when (language ?: impliedLanguage) {
                null, Language.KOTLIN -> generateKotlin(schema)
                Language.JAVA -> generateJava(schema)
            }
        }

        private fun generateJava(schema: Schema) {
            var gen = ThriftyCodeGenerator(schema, nameStyle)
            listTypeName?.let { gen = gen.withListType(it) }
            setTypeName?.let { gen = gen.withSetType(it) }
            mapTypeName?.let { gen = gen.withMapType(it) }

            val processor = TypeProcessorService.javaProcessor
            if (processor != null) {
                gen = gen.usingTypeProcessor(processor)
            }

            gen.nullabilityAnnotationType(nullabilityAnnotationType)
            gen.emitFileComment(!omitFileComments)
            gen.emitParcelable(emitParcelable)
            gen.failOnUnknownEnumValues(failOnUnknownEnumValues)
            gen.mutableFields(mutableFields)

            gen.generate(outputDirectory)
        }

        private fun generateKotlin(schema: Schema) {
            val gen = KotlinCodeGenerator(nameStyle)

            if (nullabilityAnnotationType != NullabilityAnnotationType.NONE) {
                echo("Warning: Nullability annotations are unnecessary in Kotlin and will not be generated")
            }

            if (emitParcelable) {
                gen.parcelize()
            }

            if (emitParcelable) {
                gen.parcelize()
            }

            if (omitServiceClients) {
                gen.omitServiceClients()
            }

            if (omitStructImplements) {
                gen.omitStructImplement()
            }

            if (generateServer) {
                gen.generateServer()
            }

            if (kotlinJvmName) {
                gen.emitJvmName()
            }

            if (kotlinJvmStatic) {
                gen.emitJvmStatic()
            }

            if (kotlinJvmOverloads) {
                gen.emitJvmOverloads()
            }

            if (kotlinBigEnums) {
                gen.emitBigEnums()
            }

            gen.emitFileComment(!omitFileComments)

            if (kotlinFilePerType) {
                gen.filePerType()
            } else {
                gen.filePerNamespace()
            }

            gen.failOnUnknownEnumValues(failOnUnknownEnumValues)
            gen.mutableFields(mutableFields)

            listTypeName?.let { gen.listClassName(it) }
            setTypeName?.let { gen.setClassName(it) }
            mapTypeName?.let { gen.mapClassName(it) }

            TypeProcessorService.kotlinProcessor?.let {
                gen.processor = it
            }

            val specs = gen.generate(schema)

            specs.forEach { it.writeTo(outputDirectory) }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            cli.main(args)
        } catch (e: Exception) {
            cli.echo("Unhandled exception", err = true)
            e.printStackTrace(System.err)
            exitProcess(1)
        }
    }
}
