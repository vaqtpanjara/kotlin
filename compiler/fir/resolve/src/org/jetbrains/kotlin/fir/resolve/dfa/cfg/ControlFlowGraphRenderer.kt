/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("SpellCheckingInspection")

package org.jetbrains.kotlin.fir.resolve.dfa.cfg

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.references.FirControlFlowGraphReference
import org.jetbrains.kotlin.fir.resolve.dfa.FirControlFlowGraphReferenceImpl
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.utils.DFS
import org.jetbrains.kotlin.utils.Printer
import java.util.*

class FirControlFlowGraphRenderVisitor(
    builder: StringBuilder,
) : FirVisitorVoid() {
    companion object {
        private const val EDGE = " -> "
        private const val RED = "red"
        private const val BLUE = "blue"

        private val EDGE_STYLE = EnumMap(
            mapOf(
                EdgeKind.Simple to "",
                EdgeKind.Dead to "[style=dotted]",
                EdgeKind.Cfg to "[color=green]",
                EdgeKind.Dfg to "[color=red]",
                EdgeKind.Back to "[color=green style=dashed]",
                EdgeKind.DeadBack to "[color=green style=dotted]"
            )
        )
    }

    private val printer = Printer(builder)

    private var nodeCounter = 0
    private var clusterCounter = 0
    private val indices = mutableMapOf<CFGNode<*>, Int>()

    private val topLevelGraphs = mutableSetOf<ControlFlowGraph>()
    private val allGraphs = mutableSetOf<ControlFlowGraph>()

    override fun visitFile(file: FirFile) {
        printer
            .println("digraph ${file.name.replace(".", "_")} {")
            .pushIndent()
            .println("graph [nodesep=3]")
            .println("node [shape=box penwidth=2]")
            .println("edge [penwidth=2]")
            .println()
        visitElement(file)

        for (topLevelGraph in topLevelGraphs) {
            printer.renderNodes(topLevelGraph)
            printer.renderEdges(topLevelGraph)
            printer.println()
        }

        printer
            .popIndent()
            .println("}")
    }

    private fun ControlFlowGraph.collectNodes() {
        for (node in nodes) {
            indices[node] = nodeCounter++
        }
    }

    private fun Printer.renderNodes(graph: ControlFlowGraph) {
        var color = RED
        val sortedNodes = graph.sortedNodes()
        for (node in sortedNodes) {
            if (node is EnterNodeMarker) {
                enterCluster(color)
                color = BLUE
            }
            val attributes = mutableListOf<String>()
            attributes += "label=\"${node.render().replace("\"", "")}\""

            fun fillColor(color: String) {
                attributes += "style=\"filled\""
                attributes += "fillcolor=$color"
            }

            if (node == node.owner.enterNode || node == node.owner.exitNode) {
                fillColor("red")
            }
            if (node.isDead) {
                fillColor("gray")
            } else if (node is UnionFunctionCallArgumentsNode) {
                fillColor("yellow")
            }
            println(indices.getValue(node), attributes.joinToString(separator = " ", prefix = " [", postfix = "];"))
            if (node is ExitNodeMarker) {
                exitCluster()
            }
        }
    }

    private fun Printer.renderEdges(graph: ControlFlowGraph) {
        for (node in graph.nodes) {
            if (node.followingNodes.isEmpty()) continue

            fun renderEdges(kind: EdgeKind) {
                val edges = node.followingNodes.filter { node.outgoingEdges.getValue(it) == kind }
                if (edges.isEmpty()) return
                print(
                    indices.getValue(node),
                    EDGE,
                    edges.joinToString(prefix = "{", postfix = "}", separator = " ") { indices.getValue(it).toString() }
                )
                EDGE_STYLE.getValue(kind).takeIf { it.isNotBlank() }?.let { printWithNoIndent(" $it") }
                printlnWithNoIndent(";")
            }

            for (kind in EdgeKind.values()) {
                renderEdges(kind)
            }
        }
        for (subGraph in graph.subGraphs) {
            renderEdges(subGraph)
        }
    }

    override fun visitElement(element: FirElement) {
        element.acceptChildren(this)
    }

    override fun visitControlFlowGraphReference(controlFlowGraphReference: FirControlFlowGraphReference) {
        val controlFlowGraph = (controlFlowGraphReference as? FirControlFlowGraphReferenceImpl)?.controlFlowGraph ?: return
        controlFlowGraph.collectNodes()
        if (controlFlowGraph.owner == null) {
            topLevelGraphs += controlFlowGraph
        }
        allGraphs += controlFlowGraph
    }

    private fun Printer.enterCluster(color: String) {
        println("subgraph cluster_${clusterCounter++} {")
        pushIndent()
        println("color=$color")
    }

    private fun Printer.exitCluster() {
        popIndent()
        println("}")
    }
}

private fun ControlFlowGraph.sortedNodes(): List<CFGNode<*>> {
    val nodesToSort = nodes.filterTo(mutableListOf()) { it != enterNode }
    val graphs = mutableSetOf(this)
    forEachSubGraph {
        nodesToSort += it.nodes
        graphs += it
    }

    val topologicalOrder = DFS.topologicalOrder(nodesToSort) {
        val result = if (it !is WhenBranchConditionExitNode || it.followingNodes.size < 2) {
            it.followingNodes
        } else {
            it.followingNodes.sortedBy { node -> if (node is BlockEnterNode) 1 else 0 }
        }.filter { node -> node.owner in graphs }
        result
    }
    return listOf(enterNode) + topologicalOrder
}

private fun ControlFlowGraph.forEachSubGraph(block: (ControlFlowGraph) -> Unit) {
    for (subGraph in subGraphs) {
        block(subGraph)
        subGraph.forEachSubGraph(block)
    }
}