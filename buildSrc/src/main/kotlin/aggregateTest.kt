/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test

open class AggregateTest : Test() {

    @Input
    var testTasksPath: String? = null

    @Input
    var testPatternPath: String? = null

    @Override
    @TaskAction
    override fun executeTests() {
        // Do nothing
    }
}