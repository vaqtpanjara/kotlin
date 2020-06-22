/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors.commonizer.mergedtree

import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.kotlin.name.FqName

interface CirCommonizedClassifiersCache {
    fun hasCommonizedClass(fqName: FqName): Boolean
    fun hasCommonizedTypeAlias(fqName: FqName): Boolean
}

interface CirCommonizedClassifiersCacheRW : CirCommonizedClassifiersCache {
    fun recordCommonizedClass(fqName: FqName)
    fun recordCommonizedTypeAlias(fqName: FqName)
}

class CirCommonizedClassifiersCacheImpl : CirCommonizedClassifiersCacheRW {
    private val classes = THashSet<FqName>()
    private val typeAliases = THashSet<FqName>()

    override fun hasCommonizedClass(fqName: FqName) = fqName in classes
    override fun hasCommonizedTypeAlias(fqName: FqName) = fqName in typeAliases

    override fun recordCommonizedClass(fqName: FqName) {
        classes.add(fqName)
    }

    override fun recordCommonizedTypeAlias(fqName: FqName) {
        typeAliases.add(fqName)
    }
}

interface CirClassifierNodesCache {
    fun getClassNode(fqName: FqName): CirClassNode?
    fun getTypeAliasNode(fqName: FqName): CirTypeAliasNode?
}

interface CirClassifierNodesCacheW {
    fun addClassNode(node: CirClassNode)
    fun addTypeAliasNode(node: CirTypeAliasNode)
}

class CirClassifierNodesCacheImpl(
    private val classifiersCacheRW: CirCommonizedClassifiersCacheRW
) : CirClassifierNodesCache, CirClassifierNodesCacheW, CirCommonizedClassifiersCacheRW by classifiersCacheRW {
    private val classNodes = THashMap<FqName, CirClassNode>()
    private val typeAliasNodes = THashMap<FqName, CirTypeAliasNode>()

    override fun getClassNode(fqName: FqName) = classNodes[fqName]
    override fun getTypeAliasNode(fqName: FqName) = typeAliasNodes[fqName]

    override fun addClassNode(node: CirClassNode) {
        classNodes[node.fqName] = node
    }

    override fun addTypeAliasNode(node: CirTypeAliasNode) {
        typeAliasNodes[node.fqName] = node
    }

    override fun hasCommonizedClass(fqName: FqName) = when {
        classifiersCacheRW.hasCommonizedClass(fqName) -> true
        classNodes[fqName].isCommonized() -> {
            classifiersCacheRW.recordCommonizedClass(fqName)
            true
        }
        else -> false
    }

    override fun hasCommonizedTypeAlias(fqName: FqName) = when {
        classifiersCacheRW.hasCommonizedTypeAlias(fqName) -> true
        typeAliasNodes[fqName].isCommonized() -> {
            classifiersCacheRW.recordCommonizedTypeAlias(fqName)
            true
        }
        else -> false
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun CirNode<*, *>?.isCommonized() =
        if (this == null) {
            // No node means that the class or type alias was not subject for commonization at all, probably it lays
            // not in commonized module descriptors but somewhere in their dependencies.
            true
        } else {
            // If entry is present, then contents (common declaration) should not be null.
            commonDeclaration() != null
        }
}
