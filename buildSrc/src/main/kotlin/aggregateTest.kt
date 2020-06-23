/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import java.io.File


open class AggregateTest : Test() { // Inherit from Test to see test results in IDEA Test viewer
    @Input
    lateinit var testTasksPath: String

    @Input
    lateinit var testPatternPath: String

    // Stubs to avoid exceptions when initializing a base 'Test' class
    init {
        binaryResultsDirectory.convention(project.layout.buildDirectory.dir("stub"))
        classpath = project.files("stub")
        testClassesDirs = project.files("stub")
    }

    fun configure() {
        val currentIde = IdeVersionConfigurator.currentIde.toString()
        if (File(testTasksPath).exists()) {
            File(testTasksPath)
                .readLines()
                .filter { testTask -> testTask.isNotEmpty() && !testTask.startsWith("//") }
                .forEach { testTask ->
                    if (testTask.split(",").size == 1 ||
                        testTask.split(",").any { it.trim() in currentIde }
                    ) {
                        dependsOn(testTask.split(",")[0])
                    }
                }
        }
    }

    @Override
    @TaskAction
    override fun executeTests() {
        // Do nothing
    }
}