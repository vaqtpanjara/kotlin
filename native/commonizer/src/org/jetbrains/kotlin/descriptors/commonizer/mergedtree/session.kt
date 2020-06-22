/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.mergedtree

import org.jetbrains.kotlin.descriptors.commonizer.Parameters
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.storage.StorageManager

class Session(
    val parameters: Parameters
) {
    val storageManager: StorageManager = LockBasedStorageManager("Declaration descriptors commonization")
    val classifiersCacheRW: CirCommonizedClassifiersCacheRW = CirCommonizedClassifiersCacheImpl()
}

class Round(
    val session: Session
) {
    val nodesCache: CirClassifierNodesCache
    val nodesCacheW: CirClassifierNodesCacheW
    val classifiersCache: CirCommonizedClassifiersCache

    init {
        val cacheImpl = CirClassifierNodesCacheImpl(session.classifiersCacheRW)
        nodesCache = cacheImpl
        nodesCacheW = cacheImpl
        classifiersCache = cacheImpl
    }
}
