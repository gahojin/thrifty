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

import com.microsoft.thrifty.gradle.ThriftyGradlePlugin.Companion.loadVersionProps
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.function.Function
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

class PluginTest {
    private val fixturesDir = arrayOf("src", "test", "projects").joinToString(File.separator)
    private val runner: GradleRunner = GradleRunner.create()

    @ParameterizedTest
    @ValueSource(
        strings = ["kotlin_integration_project", "java_project_kotlin_thrifts", "java_project_java_thrifts", "kotlin_project_kotlin_thrifts", "kotlin_project_java_thrifts", "kotlin_project_filtered_thrifts", "kotlin_multiple_source_dirs", "kotlin_project_with_custom_output_dir", "kotlin_project_with_include_path"],
    )
    fun integrationProjectBuildsSuccessfully(fixtureName: String) {
        val result = buildFixture(runner, fixtureName) { it.build() }
        result.task(":generateThriftFiles")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    fun typeProcessorConfigurationWorks() {
        val result = buildFixtureWithSubprojectsAndTask(
            runner,
            "kotlin_type_processor",
            listOf(":app", ":processor"),
            ":app:build",
        ) { it.build() }
        result.task(":app:generateThriftFiles")?.outcome shouldBe TaskOutcome.SUCCESS

        result.output shouldContain "I AM IN A TYPE PROCESSOR"
    }

    private fun buildFixture(
        runner: GradleRunner,
        fixtureName: String,
        buildAndAssert: Function<GradleRunner, BuildResult>
    ): BuildResult {
        return buildFixtureWithSubprojectsAndTask(
            runner,
            fixtureName,
            emptyList<String>(),
            ":build",
            buildAndAssert
        )
    }

    @OptIn(ExperimentalPathApi::class)
    private fun buildFixtureWithSubprojectsAndTask(
        runner: GradleRunner,
        fixtureName: String,
        subprojects: List<String>,
        task: String,
        buildAndAssert: Function<GradleRunner, BuildResult>
    ): BuildResult {
        val fixture = Path(fixturesDir, fixtureName)
        val settings = fixture.resolve("settings.gradle")
        val buildDirectory = fixture.resolve("build")
        val gradleDirectory = fixture.resolve(".gradle")

        settings.bufferedWriter(Charsets.UTF_8).use {
            it.appendLine("pluginManagement {")
            it.appendLine("  repositories {")
            it.appendLine("    mavenLocal()")
            it.appendLine("    gradlePluginPortal()")
            it.appendLine("  }")
            it.appendLine("  plugins {")
            it.appendLine("    id 'com.microsoft.thrifty' version '${thriftyVersion}'")
            it.appendLine("    id 'org.jetbrains.kotlin.jvm' version '${kotlinVersion}'")
            it.appendLine("  }")
            it.appendLine("}")
            it.appendLine("dependencyResolutionManagement {")
            it.appendLine("  repositories {")
            it.appendLine("    mavenLocal()")
            it.appendLine("    mavenCentral()")
            it.appendLine("  }")
            it.appendLine("  versionCatalogs {")
            it.appendLine("    msft {")
            it.appendLine("      version('thrifty', '${thriftyVersion}')")
            it.appendLine("      library('thrifty-runtime', 'com.microsoft.thrifty', 'thrifty-runtime').versionRef('thrifty')")
            it.appendLine("      library('thrifty-compilerPlugins', 'com.microsoft.thrifty', 'thrifty-compiler-plugins').versionRef('thrifty')")
            it.appendLine("    }")
            it.appendLine("  }")
            it.appendLine("}")

            for (subproject in subprojects) {
                it.appendLine("include '$subproject'")
            }
            it.flush()
        }
        try {
            val run = runner
                .withProjectDir(fixture.toFile())
                .withArguments(task, "--stacktrace", "--info", "--no-build-cache", "--no-configuration-cache")
            return buildAndAssert.apply(run)
        } finally {
            settings.deleteIfExists()
            buildDirectory.deleteRecursively()
            gradleDirectory.deleteRecursively()
        }
    }

    private val thriftyVersion: String by lazy {
        checkNotNull(loadVersionProps().getProperty("THRIFTY_VERSION"))
    }

    private val kotlinVersion: String by lazy {
        checkNotNull(loadVersionProps().getProperty("KOTLIN_VERSION"))
    }
}
