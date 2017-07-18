package moklev.asm.compiler

import moklev.asm.compiler.SSATransformer.Block
import moklev.asm.instructions.Phi
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.BranchInstruction
import moklev.asm.utils.*
import moklev.utils.Either
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

/**
 * @author Moklev Vyacheslav
 */

typealias Coloring = Map<String, StaticAssemblyValue>

data class LiveRange(val firstIndex: Int, val lastIndex: Int, val isDeadInBlock: Boolean)

data class Graph(val nodes: Set<String>, val edges: Map<String, Set<String>>)

sealed class LoopSet {
    class JustBlock(val block: Block) : LoopSet() {
        override fun toString(): String = "B[${block.label}]"

        override fun blocks(): Sequence<Block> = sequenceOf(block)
    }

    class Loop(val contents: List<LoopSet>) : LoopSet() {
        override fun toString(): String = "L$contents"

        override fun blocks(): Sequence<Block> = contents.asSequence().flatMap { it.blocks() }
    }

    abstract fun blocks(): Sequence<Block>
}

fun detectLiveRange(blocks: List<Block>): List<Pair<Block, Map<String, LiveRange>>> {
    val blockNumber = HashMap<String, Int>()
    blocks.forEachIndexed { i, block ->
        blockNumber[block.label] = i
    }
    val achievable = Array(blocks.size) {
        Array(blocks.size) {
            false
        }
    }
    blocks.forEachIndexed { i, block ->
        println("${block.label} => ${block.nextBlocks.map { it.label }}")
        for (otherBlock in block.nextBlocks) {
            val j = blockNumber[otherBlock.label]!!
            achievable[i][j] = true
        }
    }

    for (k in 0..blocks.size - 1) {
        for (i in 0..blocks.size - 1) {
            for (j in 0..blocks.size - 1) {
                achievable[i][j] = achievable[i][j] || (achievable[i][k] && achievable[k][j])
            }
        }
    }

    val usedVariables = HashMap<String, MutableSet<String>>()
    for (block in blocks) {
        val vars = HashSet<String>()
        for (instruction in block.instructions) {
            if (instruction is Phi)
                continue
            vars.addAll(instruction.usedValues
                    .asSequence()
                    .filterIsInstance<Variable>()
                    .map { "$it" }
            )
        }
        usedVariables[block.label] = vars
    }

    // Phi instruction is actually an assign instruction in the previous block
    for (block in blocks) {
        for (instruction in block.instructions) {
            if (instruction !is Phi)
                continue
            for ((label, variable) in instruction.pairs) {
                if (variable is Variable) {
                    usedVariables[label]!!.add("$variable")
                }
            }
        }
    }

    for (j in 0..blocks.size - 1) {
        for (i in 0..blocks.size - 1) {
            if (achievable[i][j]) {
                usedVariables[blocks[i].label]?.addAll(usedVariables[blocks[j].label] ?: emptySet())
            }
        }
    }

    val definedVariables = HashMap<String, MutableSet<String>>()
    for (block in blocks) {
        val vars = HashSet<String>()
        block.instructions
                .filterIsInstance<AssignInstruction>()
                .mapTo(vars) { "${it.lhs}" }
        definedVariables[block.label] = vars
    }
    while (true) {
        var changed = false
        for (j in 0..blocks.size - 1) {
            val allDefinedVariables = ArrayList<Set<String>>()
            for (parentBlock in blocks[j].prevBlocks) {
                if (parentBlock == blocks[j])
                    continue 
                allDefinedVariables.add(definedVariables[parentBlock.label]!!)
            }
            if (allDefinedVariables.isEmpty())
                continue
            val intersection = allDefinedVariables.reduce { a, b -> a intersect b }
            val addResult = definedVariables[blocks[j].label]!!.addAll(intersection)
            changed = changed || addResult
        }
        if (!changed)
            break
    }

    val liveVariables = HashMap<String, Set<String>>()
    for (block in blocks) {
        val used = usedVariables[block.label] ?: continue
        val defined = definedVariables[block.label] ?: continue
        liveVariables[block.label] = used intersect defined
    }

    println("usedVariables = $usedVariables")
    println("definedVariables = $definedVariables")
    println("liveVariables = $liveVariables")

    return blocks.map {
        val block = it
        block to liveVariables[it.label]!!.map {
            val variable = it
            val isDeadInBlock = block.nextBlocks.all { variable !in liveVariables[it.label]!! }
            variable to LiveRange(
                    block.instructions.indexOfFirst {
                        it !is Phi && it is AssignInstruction && it.lhs.toString() == variable ||
                                it is BranchInstruction && run {
                                    val branch = it
                                    val destBlock = blocks.first { it.label == branch.label }
                                    destBlock.instructions
                                            .filterIsInstance<Phi>()
                                            .any { "${it.lhs}" == variable }
                                }
                    }.let { if (it == -1) 0 else it },
                    if (isDeadInBlock)
                        block.instructions.indexOfLast {
                            it.usedValues.any { it is Variable && it.toString() == variable } ||
                                    it is BranchInstruction && run {
                                        val branch = it
                                        val destBlock = blocks.first { it.label == branch.label }
                                        destBlock.instructions
                                                .filterIsInstance<Phi>()
                                                .any {
                                                    it.pairs.firstOrNull {
                                                        it.second is Variable && "$it" == variable
                                                    } != null
                                                }
                                    }
                        }
                    else
                        block.instructions.size - 1,
                    isDeadInBlock
            )
        }.toMap()
    }
}

fun buildConflictGraph(liveRanges: List<Pair<Block, Map<String, LiveRange>>>): Graph {
    val nodes = HashSet<String>()
    liveRanges.flatMapTo(nodes) {
        it.second.keys
    }

    val edges = HashMap<String, MutableSet<String>>()
    nodes.forEach {
        edges[it] = HashSet()
    }

    liveRanges.forEach { (_, map) ->
        for (u in nodes) {
            for (v in nodes) {
                if (u == v)
                    continue
                if (u in map && v in map) {
                    val uRange = map[u]!!
                    val vRange = map[v]!!
                    if (uRange intersects vRange) {
                        edges[u]?.add(v)
                    }
                }
            }
        }
    }

    return Graph(nodes, edges)
}

private infix fun LiveRange.intersects(b: LiveRange): Boolean {
    return firstIndex in b.firstIndex..(b.lastIndex - 1) ||
            b.firstIndex in firstIndex..(lastIndex - 1)
}

private fun detectLoops(blocks: List<Block>): List<LoopSet> {
    val achievable = Array(blocks.size) {
        Array(blocks.size) {
            false
        }
    }
    val indexByLabel = HashMap<String, Int>()
    blocks.forEachIndexed { i, block ->
        indexByLabel[block.label] = i
    }

    for (i in 0..blocks.size - 1) {
        for (block in blocks[i].nextBlocks) {
            val j = indexByLabel[block.label] ?: continue
            achievable[i][j] = true
        }
    }

    for (k in 0..blocks.size - 1) {
        for (i in 0..blocks.size - 1) {
            for (j in 0..blocks.size - 1) {
                achievable[i][j] = achievable[i][j] || achievable[i][k] && achievable[k][j]
            }
        }
    }

    val result = ArrayList<LoopSet>()
    val used = Array(blocks.size) { false }
    blocks.forEachIndexed { i, block ->
        if (used[i])
            return@forEachIndexed
        if (!achievable[i][i]) {
            result.add(LoopSet.JustBlock(block))
            used[i] = true
        } else {
            val inLoop = Array(blocks.size) {
                achievable[it][i] && achievable[i][it]
            }

            val startBlock = blocks.first {
                inLoop[indexByLabel[it.label]!!] && it.prevBlocks.any {
                    !inLoop[indexByLabel[it.label]!!]
                }
            }
            val removedParents = ArrayList<Block>()
            for (prevBlock in startBlock.prevBlocks) {
                removedParents.add(prevBlock)
                prevBlock.nextBlocks.remove(startBlock)
            }
            startBlock.prevBlocks.clear()
            result.add(LoopSet.Loop(detectLoops(blocks.filterIndexed {
                j, _ ->
                inLoop[j]
            })))
            for (j in 0..blocks.size - 1) {
                if (inLoop[j])
                    used[j] = true
            }
            for (prevBlock in removedParents) {
                prevBlock.nextBlocks.add(startBlock)
            }
            startBlock.prevBlocks.addAll(removedParents)
        }
    }
    return result
}

/**
 * Assign a memory location for every variable from
 * a collection of blocks
 *
 * @param colors order preference of colors
 * @param initialColoring initial mapping from `Block.label` to `(var, color)`
 * @param conflictGraph conflict graph of variables live ranges
 * @param blocks collection of blocks to color
 */
fun advancedColorGraph(
        colors: List<InRegister>,
        initialColoring: Map<String, Coloring>,
        conflictGraph: Graph,
        coalescingEdges: Map<String, Set<Pair<String, Either<InRegister, String>>>>,
        blocks: List<Block>
): VariableAssignment {
    val result = HashMap<String, Coloring>()
    val loopSets = detectLoops(blocks)
    val globalLoopSet = LoopSet.Loop(loopSets)
    colorLoopSet(
            colors,
            initialColoring,
            conflictGraph,
            coalescingEdges,
            globalLoopSet,
            result
    )
    return result
}

private fun colorLoopSet(
        colors: List<InRegister>,
        initialColoring: Map<String, Coloring>,
        conflictGraph: Graph,
        coalescingEdges: Map<String, Set<Pair<String, Either<InRegister, String>>>>,
        loopSet: LoopSet,
        coloredBlocks: MutableMap<String, Coloring>
) {
    when (loopSet) {
        is LoopSet.JustBlock -> {
            println("IsBlock(${loopSet.block.label})")
            val coloring = colorBlocks(
                    colors,
                    initialColoring,
                    conflictGraph,
                    coalescingEdges,
                    loopSet.blocks().toList(),
                    coloredBlocks,
                    failOnStack = false
            ).right()
            coloredBlocks[loopSet.block.label] = coloring
        }
        is LoopSet.Loop -> {
            println("IsLoop(${loopSet.blocks().joinToString { it.label }})")
            colorBlocks(
                    colors,
                    initialColoring,
                    conflictGraph,
                    coalescingEdges,
                    loopSet.blocks().toList(),
                    coloredBlocks
            ).map({
                for (subLoopSet in loopSet.contents) {
                    colorLoopSet(
                            colors,
                            initialColoring,
                            conflictGraph,
                            coalescingEdges,
                            subLoopSet,
                            coloredBlocks
                    )
                }
            }) {
                for (block in loopSet.blocks()) {
                    coloredBlocks[block.label] = it
                }
            }
        }
    }
}

private fun colorBlocks(
        colors: List<InRegister>,
        initialColoring: Map<String, Coloring>,
        conflictGraph: Graph,
        coalescingEdges: Map<String, Set<Pair<String, Either<InRegister, String>>>>,
        blocks: List<Block>,
        coloredBlocks: Map<String, Coloring>,
        failOnStack: Boolean = true
): Either<Unit, Coloring> {
    val currentColoring = initialColoring.map { it.key to it.value.toMutableMap() }.toMap()
    var tempIndex = 0
    fun mergeNodes(graph: Graph, node1: String, node2: String): Pair<Graph, String>? {
        val leftColor = currentColoring[node1]
        val rightColor = currentColoring[node2]
        if (leftColor != null && rightColor != null && leftColor != rightColor)
            return null
        val newNodes = HashSet(graph.nodes)
        val newEdges = HashMap(graph.edges.mapValues { it.value.toMutableSet() })
        if (!newNodes.remove(node1))
            return null
        if (!newNodes.remove(node2))
            return null
        val newVar = "#merged_$tempIndex"
        tempIndex++
        newNodes.add(newVar)
        val leftNeighbours = newEdges[node1]!!
        val rightNeighbours = newEdges[node2]!!
        if (node1 in rightNeighbours || node2 in leftNeighbours)
            return null
        newEdges.remove(node1)
        newEdges.remove(node2)
        newEdges[newVar] = HashSet(leftNeighbours union rightNeighbours)
        for (variable in newEdges[newVar]!!) {
            newEdges[variable]?.add(newVar)
        }
        return Graph(newNodes, newEdges) to newVar
    }

    val coalesce = HashSet<Pair<String, String>>()
    coalescingEdges.forEach {
        it.value
                .filter { it.second is Either.Right }
                .mapTo(coalesce) { it.first to it.second.right() }
    }
    val targeting = HashSet<Pair<String, InRegister>>()
    coalescingEdges.forEach {
        it.value
                .filter { it.second is Either.Left }
                .mapTo(targeting) { it.first to it.second.left() }
    }

    println("[colorBlocks]: coalescingEdges = $coalescingEdges")
    println("[colorBlocks]: coalesce = $coalesce")
    println("[colorBlocks]: targeting = $targeting")

    var currentGraph = conflictGraph
    var bestColoring = colorBlocks(colors, currentColoring, targeting, conflictGraph, blocks, coloredBlocks, failOnStack)
            .map({
                return Either.Left(Unit)
            }) { it }.right()
    val stackAllocated = bestColoring.count { it.value is InStack }
    println("STACK_ALLOCATED: $stackAllocated")
    val mergedNodesMap = HashMap<String, Pair<String, String>>()
    while (coalesce.isNotEmpty()) {
        var success = false
        for ((lVar, rVar) in coalesce) {
            val (newGraph, newVar) = mergeNodes(currentGraph, lVar, rVar) ?: continue
            for (coloring in currentColoring.values) {
                val oldLeftColor = coloring[lVar]
                val oldRightColor = coloring[rVar]
                if (oldLeftColor != null)
                    coloring[newVar] = oldLeftColor
                if (oldRightColor != null)
                    coloring[newVar] = oldRightColor
            }
            val lVarTarget = HashSet<Pair<String, InRegister>>()
            val rVarTarget = HashSet<Pair<String, InRegister>>()
            targeting.filterTo(lVarTarget) { (varName, _) ->
                varName == lVar
            }
            targeting.filterTo(rVarTarget) { (varName, _) ->
                varName == rVar
            }
            targeting.removeAll(lVarTarget)
            targeting.removeAll(rVarTarget)
            targeting.addAll(
                    sequenceOf(lVarTarget, rVarTarget)
                            .flatMap { it.asSequence() }
                            .map { (_, place) -> newVar to place }
            )
            println("CUR_COL: $currentColoring")
            val coloring = colorBlocks(
                    colors,
                    currentColoring,
                    targeting,
                    newGraph,
                    blocks,
                    coloredBlocks,
                    failOnStack
            )
            if (coloring !is Either.Right) {
                targeting.removeIf { (varName, _) -> varName == newVar }
                targeting.addAll(lVarTarget)
                targeting.addAll(rVarTarget)
                for (blockColoring in currentColoring.values) {
                    blockColoring.remove(newVar)
                }
                continue
            }
            val newStackAllocated = coloring.value.count { it.value is InStack }
            println("NEWWWWWWWW: $newStackAllocated")
//            if (newStackAllocated < stackAllocated)
//                error("WAT") // TODO solve if there is a way to achieve
            if (newStackAllocated > stackAllocated)
                continue
            mergedNodesMap[newVar] = lVar to rVar
            currentGraph = newGraph
            bestColoring = coloring.value
            coalesce.remove(lVar to rVar)
            val newCoalescingEdges = ArrayList<Pair<String, String>>()
            for ((left, right) in coalesce) {
                if (left == lVar || left == rVar)
                    newCoalescingEdges.add(newVar to right)
                if (right == lVar || right == rVar)
                    newCoalescingEdges.add(left to newVar)
            }
            coalesce.removeIf { (left, right) ->
                left == lVar || left == rVar || right == lVar || right == rVar
            }
            coalesce.addAll(newCoalescingEdges)
            success = true
            println("Merged: $lVar <=> $rVar")
            break
        }
        if (!success)
            break
    }

    println("MAMKA_COLORING: $bestColoring")

    println(mergedNodesMap)

    val resultColoring = HashMap(bestColoring)
    while (true) {
        val compoundNode = resultColoring.keys.firstOrNull {
            it in mergedNodesMap
        } ?: break
        val (left, right) = mergedNodesMap[compoundNode]!!
        val color = resultColoring[compoundNode]!!
        resultColoring.remove(compoundNode)
        resultColoring[left] = color
        resultColoring[right] = color
    }

    println("PAPKA_COLORING: $resultColoring")

    return Either.Right(resultColoring)
}

private fun colorBlocks(
        colors: List<InRegister>,
        initialColoring: Map<String, Coloring>,
        targeting: Set<Pair<String, InRegister>>,
        conflictGraph: Graph,
        blocks: List<Block>,
        coloredBlocks: Map<String, Coloring>,
        failOnStack: Boolean = true
): Either<Unit, Coloring> {
    println()
    println("/***************** [colorBlocks] ****************/")
    println("conflictGraph = $conflictGraph")
    println("/*************** [colorBlocks::end] ****************/")
    println()
    val nbColors = colors.size
    val nodes = conflictGraph.nodes

    val startBlock = run {
        val labelSet = blocks
                .asSequence()
                .map { it.label }
                .toSet()
        blocks
                .asSequence()
                .filter { it.prevBlocks.any { it.label !in labelSet } }
                .singleOrNull()
    }

    val nbNodes = nodes.size
    val matrix = Array(nbNodes) {
        BooleanArray(nbNodes)
    }
    val nodeToIndex = HashMap<String, Int>()
    val indexToNode = Array<String?>(nbNodes) { null }
    nodes.forEachIndexed { index, node ->
        nodeToIndex[node] = index
        indexToNode[index] = node
    }

    nodes.forEachIndexed { from, node ->
        println(node)
        val neighbours = conflictGraph.edges[node]!!
        for (other in neighbours) {
            val to = nodeToIndex[other] ?: continue
            matrix[from][to] = true
            matrix[to][from] = true
        }
    }

    val spillCost = IntArray(nbNodes)
    for ((_, coloring) in initialColoring) {
        for ((variable, _) in coloring) {
            val index = nodeToIndex[variable] ?: continue
            spillCost[index] = Int.MAX_VALUE
        }
    }

    println("Coloring: ${(0..nbNodes - 1).map { indexToNode[it] }}")
    val result = colorGraph(nbColors, nbNodes, matrix, spillCost)

    val indexToColor = HashMap<Int, StaticAssemblyValue>()
    val remainingColors = LinkedHashSet(colors)
    for ((_, coloring) in initialColoring) {
        for ((variable, color) in coloring) {
            val nodeIndex = nodeToIndex[variable] ?: continue
            if (indexToColor[result[nodeIndex]] == color)
                throw IllegalArgumentException("Conflicting initial coloring")
            indexToColor[result[nodeIndex]] = color
            remainingColors.remove(color)
            println("${result[nodeIndex]} <= $color")
            println("remain: $remainingColors")
        }
    }

    val reservedOffsets = HashSet<Int>()
    val resultColoring = HashMap<String, StaticAssemblyValue>()

    nodes.forEachIndexed { index, node ->
        fun preferredColor(): StaticAssemblyValue? {
            var preferredColor: StaticAssemblyValue? = targeting.firstOrNull { it.first == node }?.second
            if (startBlock?.usedVariables?.contains(node) ?: false) {
                preferredColor = startBlock!!.prevBlocks
                        .asSequence()
                        .mapNotNull { coloredBlocks[it.label] }
                        .mapNotNull { it[node] }
                        .groupingBy { it }
                        .eachCount()
                        .maxBy { it.value }
                        ?.key
            }
            return preferredColor
        }


        val colorIndex = result[index]
        if (colorIndex < 0) {
            if (failOnStack)
                return Either.Left(Unit)
            val preferredColor = preferredColor() as? InStack
            if (preferredColor != null) {
                resultColoring[node] = preferredColor
                reservedOffsets.add(preferredColor.offset)
            }
        } else if (indexToColor[colorIndex] == null) {
            val preferredColor = preferredColor() as? InRegister
            if (preferredColor != null && preferredColor in remainingColors) {
                indexToColor[colorIndex] = preferredColor
                remainingColors.remove(preferredColor)
                resultColoring[node] = preferredColor
            }
        }
    }

    println(Arrays.toString(result))

    nodes.forEachIndexed { index, node ->
        val colorIndex = result[index]
        if (colorIndex < 0) {
            if (resultColoring[node] == null) {
                val offset = (1..nodes.size)
                        .asSequence()
                        .map { it * 8 }
                        .filter { it !in reservedOffsets }
                        .first()
                reservedOffsets.add(offset)
                resultColoring[node] = InStack(offset)
            }
        } else if (indexToColor[colorIndex] == null) {
            val color = remainingColors.first()
            remainingColors.remove(color)
            indexToColor[colorIndex] = color
        }
        if (colorIndex >= 0) {
            resultColoring[node] = indexToColor[colorIndex]!!
        }
    }

    println(indexToColor)

    return Either.Right(resultColoring)
}

private fun colorGraph(nbColors: Int, nbNodes: Int, graph: Array<BooleanArray>, spillCost: IntArray): Array<Int> {
    val dropNodes = ArrayList<Int>()
    val degrees = Array(nbNodes) {
        graph[it].count { it }
    }
    val isDropNode = Array(nbNodes) { false }
    while (dropNodes.size < nbNodes) {
        val dropNode = (0..nbNodes - 1)
                .asSequence()
                .filter { !isDropNode[it] && degrees[it] < nbColors }
                .maxBy { degrees[it] }
                ?: (0..nbNodes - 1)
                .asSequence()
                .filter { !isDropNode[it] }
                .minBy { spillCost[it] }!!
        isDropNode[dropNode] = true
        dropNodes.add(dropNode)
        for (i in 0..nbNodes - 1)
            if (graph[dropNode][i])
                degrees[i] -= 1
    }
    val colors = Array(nbNodes) { -1 }
    var inStackCount = 0
    for (node in dropNodes.reversed()) {
        val usedColors = HashSet<Int>()
        (0..nbNodes - 1)
                .asSequence()
                .filter { !isDropNode[it] && graph[it][node] }
                .mapTo(usedColors) {
                    colors[it]
                }
        for (color in 0..nbColors - 1) {
            if (color !in usedColors) {
                colors[node] = color
                break
            }
        }
        if (colors[node] < 0) {
            inStackCount++
            colors[node] = -inStackCount * 8
        }
        isDropNode[node] = false
    }
    return colors
}