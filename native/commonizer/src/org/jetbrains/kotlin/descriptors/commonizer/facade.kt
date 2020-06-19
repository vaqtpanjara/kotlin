/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.commonizer.builder.DeclarationsBuilderVisitor1
import org.jetbrains.kotlin.descriptors.commonizer.builder.DeclarationsBuilderVisitor2
import org.jetbrains.kotlin.descriptors.commonizer.builder.createGlobalBuilderComponents
import org.jetbrains.kotlin.descriptors.commonizer.core.CommonizationVisitor
import org.jetbrains.kotlin.descriptors.commonizer.mergedtree.CirTreeMerger
import org.jetbrains.kotlin.storage.LockBasedStorageManager

fun runCommonization(parameters: Parameters): Result {
    if (!parameters.hasAnythingToCommonize())
        return NothingToCommonize

    val storageManager = LockBasedStorageManager("Declaration descriptors commonization")

    checkpoint("START")

    // build merged tree:
    val mergedTree = CirTreeMerger(storageManager, parameters).merge()

    checkpoint("MERGED")
//    pause()

    // commonize:
    mergedTree.accept(CommonizationVisitor(mergedTree), Unit)
    parameters.progressLogger?.invoke("Commonized declarations")

    checkpoint("COMMONIZED")

    // build resulting descriptors:
    val components = mergedTree.createGlobalBuilderComponents(storageManager, parameters)
    mergedTree.accept(DeclarationsBuilderVisitor1(components), emptyList())
    mergedTree.accept(DeclarationsBuilderVisitor2(components), emptyList())

    checkpoint("BUILT DESCRIPTORS")

    val modulesByTargets = LinkedHashMap<Target, Collection<ModuleDescriptor>>() // use linked hash map to preserve order
    components.targetComponents.forEach {
        val target = it.target
        check(target !in modulesByTargets)

        modulesByTargets[target] = components.cache.getAllModules(it.index)
    }

    parameters.progressLogger?.invoke("Prepared new descriptors")

    return CommonizationPerformed(modulesByTargets)
}

private fun pause() {
//    repeat(20) {
//        System.gc()
//    }
//    Thread.sleep(1000)
//    println("!!!!! CAPTURE MEMORY SNAPSHOT NOW !!!!!")
//    Thread.sleep(1000 * 30)
}

private fun checkpoint(name: String) {
//    fun printMemoryUsage(message: String) {
//        val runtime = Runtime.getRuntime()
//
//        val free = runtime.freeMemory()
//        val total = runtime.totalMemory()
//        val used = total - free
//        val max = runtime.maxMemory()
//
//        fun Long.toHumanPresentation() = "${(this / 1024 / 1024)}MB"
//
//        println(
//            """
//            $message:
//            - Used:  ${used.toHumanPresentation()}
//            - Free:  ${free.toHumanPresentation()}
//            - Total: ${total.toHumanPresentation()}
//            - Max:   ${max.toHumanPresentation()}
//        """.trimIndent()
//        )
//    }
//
//    println()
//    println("*** Checkpoint \"$name\" ***")
//    printMemoryUsage("Before-GC memory usage")
//    print("Triggering GC...")
//
//    repeat(20) {
//        System.gc()
//    }
////    Thread.sleep(1000 * 30)
//
//    println(" Done")
//    printMemoryUsage("After-GC memory usage")
//    println()
}
