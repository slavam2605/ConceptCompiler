package moklev.asm.compiler

import moklev.asm.compiler.SSATransformer.Block
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.utils.*
import moklev.utils.Either
import java.rmi.registry.Registry
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
            // TODO decide what to do with variables occur only as an rhs of Phi instruction
            vars.addAll(instruction.usedValues
                    .asSequence()
                    .filterIsInstance<Variable>()
                    .map { "$it" }
            )
        }
        usedVariables[block.label] = vars
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
    for (i in 0..blocks.size - 1) {
        for (j in 0..blocks.size - 1) {
            if (achievable[i][j]) {
                definedVariables[blocks[j].label]?.addAll(definedVariables[blocks[i].label] ?: emptySet())
            }
        }
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
                        it is AssignInstruction && it.lhs.toString() == variable
                    }.let { if (it == -1) 0 else it },
                    if (isDeadInBlock)
                        block.instructions.indexOfLast {
                            it.usedValues.any { it is Variable && it.toString() == variable }
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
            // TODO maybe change algorithm to separate loops of kind (((block) block) block)
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
        blocks: List<Block>
): VariableAssignment {
    val result = HashMap<String, Coloring>()
    val loopSets = detectLoops(blocks)
    val globalLoopSet = LoopSet.Loop(loopSets)
    colorLoopSet(
            colors,
            initialColoring,
            conflictGraph,
            globalLoopSet,
            result
    )
    return result
}

private fun colorLoopSet(
        colors: List<InRegister>,
        initialColoring: Map<String, Coloring>,
        conflictGraph: Graph,
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
                    loopSet.blocks().toList(),
                    coloredBlocks
            ).map({
                for (subLoopSet in loopSet.contents) {
                    colorLoopSet(
                            colors,
                            initialColoring,
                            conflictGraph,
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
        blocks: List<Block>,
        coloredBlocks: Map<String, Coloring>,
        failOnStack: Boolean = true
): Either<Unit, Coloring> {
    val nbColors = colors.size
    val nodes = blocks
            .asSequence()
            .flatMap { it.usedVariables.asSequence() }
            .distinct()
            .toList()

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
        }
    }

    val reservedOffsets = HashSet<Int>()
    val resultColoring = HashMap<String, StaticAssemblyValue>()

    nodes.forEachIndexed { index, node ->
        fun preferredColor(): StaticAssemblyValue? {
            var preferredColor: StaticAssemblyValue? = null
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
            if (preferredColor != null) {
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

    return Either.Right(resultColoring)
}

fun colorGraph(colors: Set<InRegister>, initialColoring: Coloring, graph: Graph): Coloring {
    val nbColors = colors.size
    val nbNodes = graph.nodes.size
    val matrix = Array(nbNodes) {
        BooleanArray(nbNodes)
    }
    val nodeToIndex = HashMap<String, Int>()
    val indexToNode = Array<String?>(nbNodes) { null }
    var index = 0
    for (node in graph.nodes) {
        nodeToIndex[node] = index
        indexToNode[index] = node
        index += 1
    }
    for ((node, neighbours) in graph.edges) {
        val from = nodeToIndex[node]!!
        for (other in neighbours) {
            val to = nodeToIndex[other]!!
            matrix[from][to] = true
            matrix[to][from] = true
        }
    }
    val result = colorGraph(nbColors, nbNodes, matrix, IntArray(nbNodes))
    val indexToColor = HashMap<Int, StaticAssemblyValue>()
    val remainingColors = HashSet(colors)
    for ((variable, color) in initialColoring) {
        val nodeIndex = nodeToIndex[variable] ?: continue
        indexToColor[result[nodeIndex]] = color
        remainingColors.remove(color)
    }
    for (i in 0..nbColors - 1) {
        if (indexToColor[i] == null) {
            val color = remainingColors.first()
            indexToColor[i] = color
            remainingColors.remove(color)
        }
    }
    return result
            .asSequence()
            .mapIndexed { i, colorIndex ->
                indexToNode[i]!! to if (colorIndex < 0)
                    InStack(-colorIndex)
                else
                    indexToColor[colorIndex]!!
            }
            .toMap()
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