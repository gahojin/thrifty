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

import org.gradle.api.Action
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import javax.inject.Inject

/**
 * Implements the 'thrifty' Gradle extension.
 *
 *
 * This is the public interface of our Gradle plugin to build scripts.  Renaming
 * or removing a method is a breaking change!
 */
abstract class ThriftyExtension @Inject constructor(
    private val objects: ObjectFactory,
    private val layout: ProjectLayout
) {
    private val includePathEntries: ListProperty<Directory> = objects.listProperty(Directory::class.java)
    private val sources: ListProperty<DefaultThriftSourceDirectory> = objects.listProperty(DefaultThriftSourceDirectory::class.java)
        .convention(listOf(DefaultThriftSourceDirectory(defaultSourceDirectorySet)))
    val thriftOptions: Property<ThriftOptions> = objects.property(ThriftOptions::class.java)
        .convention(KotlinThriftOptions())
    private val outputDirectory: DirectoryProperty = objects.directoryProperty()
        .convention(layout.buildDirectory.dir(DEFAULT_OUTPUT_DIR))
    val thriftyVersion: Property<String> = objects.property(String::class.java)

    private val defaultSourceDirectorySet: SourceDirectorySet
        get() = objects.sourceDirectorySet("thrift-sources", "Thrift Sources")
            .srcDir(DEFAULT_SOURCE_DIR)
            .include("**/*.thrift") as SourceDirectorySet

    fun getIncludePathEntries(): Provider<List<Directory>> {
        return includePathEntries
    }

    val includePath: Provider<List<File>>
        get() = getIncludePathEntries().map { dirs -> dirs.map { it.asFile } }

    fun getSources(): Provider<List<DefaultThriftSourceDirectory>> {
        return sources
    }

    val sourceDirectorySets: Provider<List<SourceDirectorySet>>
        get() = getSources().map { ss -> ss.map { it.sourceDirectorySet } }

    fun getThriftOptions(): Provider<ThriftOptions> {
        return thriftOptions
    }

    fun getOutputDirectory(): Provider<Directory> {
        return outputDirectory
    }

    fun sourceDir(path: String): ThriftSourceDirectory {
        val sd = objects.sourceDirectorySet("thrift-sources", "Thrift Sources")
        sd.srcDir(path)

        val dtsd: DefaultThriftSourceDirectory = objects.newInstance(DefaultThriftSourceDirectory::class.java, sd)
        sources.add(dtsd)

        return dtsd
    }

    fun sourceDir(path: String, action: Action<ThriftSourceDirectory>): ThriftSourceDirectory {
        val tsd = sourceDir(path)
        action.execute(tsd)
        return tsd
    }

    fun sourceDirs(vararg paths: String): List<ThriftSourceDirectory> {
        return paths.map { sourceDir(it) }
    }

    fun includePath(vararg paths: String) {
        for (path in paths) {
            val dir = layout.projectDirectory.dir(path)
            require(dir.asFile.isDirectory()) { "Include-path '$path' is not a directory" }
            includePathEntries.add(dir)
        }
    }

    fun outputDir(path: String) {
        val f = File(path)
        if (f.isAbsolute) {
            outputDirectory.fileValue(f)
        } else {
            outputDirectory.value(layout.projectDirectory.dir(path))
        }
    }

    fun kotlin(action: Action<KotlinThriftOptions>) {
        val opts: KotlinThriftOptions = objects.newInstance(KotlinThriftOptions::class.java)
        action.execute(opts)
        thriftOptions.set(opts)
    }

    fun java(action: Action<JavaThriftOptions>) {
        val opts: JavaThriftOptions = objects.newInstance(JavaThriftOptions::class.java)
        action.execute(opts)
        thriftOptions.set(opts)
    }

    companion object {
        private val DEFAULT_SOURCE_DIR: String = arrayOf("src", "main", "thrift").joinToString(File.separator)
        private val DEFAULT_OUTPUT_DIR: String = arrayOf("generated", "sources", "thrifty").joinToString(File.separator)
    }
}
