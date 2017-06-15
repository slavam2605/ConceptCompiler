package moklev.asm.compiler

import moklev.asm.compiler.SSATransformer.Block
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.utils.Variable
import java.util.*
import kotlin.collections.HashSet

/**
 * @author Moklev Vyacheslav
 */
object RegisterAllocation {
    data class LiveRange(val firstIndex: Int, val lastIndex: Int, val isDeadInBlock: Boolean)
    data class Graph(val nodes: Set<String>, val edges: Map<String, Set<String>>)
    
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

        println(usedVariables)
        println(definedVariables)
        println(liveVariables)

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
        if (firstIndex == lastIndex || b.firstIndex == b.lastIndex)
            return false
        return firstIndex in b.firstIndex..(b.lastIndex - 1) ||
                b.firstIndex in firstIndex..(lastIndex - 1)
    }

    fun colorGraph(colors: Set<String>, initialColoring: Map<String, String>, graph: Graph): Map<String, String> {
        val nbColors = colors.size
        val nbNodes = graph.nodes.size
        val matrix = Array(nbNodes) {
            Array(nbNodes) {
                false
            }
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
        val result = colorGraph(nbColors, nbNodes, matrix)
        val indexToColor = HashMap<Int, String>()
        val remainingColors = HashSet(colors)
        for ((variable, color) in initialColoring) {
            val nodeIndex = nodeToIndex[variable]!!
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
                    indexToNode[i]!! to indexToColor[colorIndex]!!
                }
                .toMap()
    }

    private fun colorGraph(nbColors: Int, nbNodes: Int, graph: Array<Array<Boolean>>): Array<Int> {
        val dropNodes = ArrayList<Int>()
        val degrees = Array(nbNodes) {
            graph[it].count { it }
        }
        val isDropNode = Array(nbNodes) { false }
        while (dropNodes.size < nbNodes) {
            val dropNode = (0..nbNodes - 1)
                    .filter { !isDropNode[it] && degrees[it] < nbColors }
                    .maxBy { degrees[it] } 
                    ?: error("No node with degree less than $nbColors (nbColors)")
            isDropNode[dropNode] = true
            dropNodes.add(dropNode)
            for (i in 0..nbNodes - 1)
                if (graph[dropNode][i])
                    degrees[i] -= 1
        }
        val colors = Array(nbNodes) { 0 }
        for (node in dropNodes.reversed()) {
            val usedColors = HashSet<Int>()
            (0..nbNodes - 1)
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
            isDropNode[node] = false
        }
        return colors
    }
}