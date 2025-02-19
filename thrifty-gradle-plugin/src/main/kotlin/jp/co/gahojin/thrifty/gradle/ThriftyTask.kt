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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * The Gradle task responsible for triggering generation of Thrifty source files.
 *
 * In practice, just a thin layer around a Worker API action which does the heavy
 * lifting.
 */
abstract class ThriftyTask : SourceTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    abstract val includePath: ListProperty<File>

    @get:Nested
    abstract val thriftOptions: Property<ThriftOptions>

    @get:Internal
    abstract val showStacktrace: Property<ShowStacktrace>

    @get:Classpath
    abstract val thriftyClasspath: ConfigurableFileCollection

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun run() {
        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.from(thriftyClasspath)
        }

        workQueue.submit(GenerateThriftSourcesWorkAction::class.java) {
            it.outputDirectory.set(outputDirectory)
            it.includePath.set(includePath)
            it.source.from(source)
            it.thriftOptions.set(SerializableThriftOptions(thriftOptions.get()))
            it.showStacktrace.set(showStacktrace)
        }
    }
}
