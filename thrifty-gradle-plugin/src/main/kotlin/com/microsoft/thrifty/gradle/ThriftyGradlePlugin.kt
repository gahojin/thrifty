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

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.*

/**
 * The plugin makes everything happen.
 */
abstract class ThriftyGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val props = loadVersionProps()
        val version = props.getProperty("THRIFTY_VERSION", "")
        check(version.isNotEmpty()) { "Missing THRIFTY_VERSION property" }

        val ext = project.extensions.create("thrifty", ThriftyExtension::class.java)
        ext.thriftyVersion.convention(version)

        val thriftyConfig = createConfiguration(project, ext.thriftyVersion)
        createTypeProcessorConfiguration(project, thriftyConfig)

        val thriftTaskProvider= project.tasks.register("generateThriftFiles", ThriftyTask::class.java) {
            it.group = "thrifty"
            it.description = "Generate Thrifty thrift implementations for .thrift files"
            it.includePath.set(ext.includePath)
            it.outputDirectory.set(ext.getOutputDirectory())
            it.thriftOptions.set(ext.getThriftOptions())
            it.showStacktrace.set(project.gradle.startParameter.showStacktrace)
            it.thriftyClasspath.from(thriftyConfig)
            it.source(ext.sourceDirectorySets)
        }

        project.plugins.withType(JavaBasePlugin::class.java).configureEach {
            val extension = project.extensions.getByType(JavaPluginExtension::class.java)
            extension.sourceSets.configureEach {
                if (it.name == "main") {
                    it.java.srcDir(thriftTaskProvider)
                }
            }
        }
    }

    private fun createConfiguration(project: Project, thriftyVersion: Provider<String>): Configuration {
        val configuration = project.configurations.create("thriftyGradle") {
            it.description = "configuration for the Thrifty Gradle Plugin"
            it.isVisible = false
            it.isTransitive = true
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
        }

        configuration.defaultDependencies {
            it.add(project.dependencies.create("com.microsoft.thrifty:thrifty-schema:${thriftyVersion.get()}"))
            it.add(project.dependencies.create("com.microsoft.thrifty:thrifty-java-codegen:${thriftyVersion.get()}"))
            it.add(project.dependencies.create("com.microsoft.thrifty:thrifty-kotlin-codegen:${thriftyVersion.get()}"))
            it.add(project.dependencies.create("com.microsoft.thrifty:thrifty-compiler-plugins:${thriftyVersion.get()}"))
        }

        return configuration
    }

    private fun createTypeProcessorConfiguration(project: Project, thriftyConfiguration: Configuration) {
        project.configurations.create("thriftyTypeProcessor") {
            it.description = "dependencies containing Thrifty type processors"
            it.isVisible = true
            it.isTransitive = true
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            thriftyConfiguration.extendsFrom(it)
        }
    }

    companion object {
        @JvmStatic
        @VisibleForTesting
        fun loadVersionProps(): Properties {
            return try {
                this::class.java.classLoader.getResource("thrifty-version.properties")?.openStream().use {
                    Properties().apply { load(it) }
                }
            } catch (e: IOException) {
                throw GradleException("BOOM", e)
            }
        }
    }
}
