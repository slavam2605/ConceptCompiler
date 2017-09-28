package moklev.asm.compiler

import moklev.asm.compiler.SSATransformer.Block
import moklev.asm.instructions.Phi
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.BranchInstruction
import moklev.asm.utils.*

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
        definedVariables[block.label] = block.instructions
                .filterIsInstance<AssignInstruction>()
                .mapTo(HashSet()) { "${it.lhs}" }
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
            // TODO maybe it is not correct -- check
//            val intersection = allDefinedVariables.reduce { a, b -> a intersect b }
//            val addResult = definedVariables[blocks[j].label]!!.addAll(intersection)
//            changed = changed || addResult
            allDefinedVariables.forEach { defined ->
                val addResult = definedVariables[blocks[j].label]!!.addAll(defined)
                changed = changed || addResult
            }
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

    return blocks.map { block ->
        block to liveVariables[block.label]!!.map { variable ->
            val isDeadInBlock = block.nextBlocks.all { variable !in liveVariables[it.label]!! }
            variable to LiveRange(
                    run {
                        if (block.prevBlocks
                                .asSequence()
                                .map { it.label }
                                .any { variable in liveVariables[it]!! })
                            0
                        else
                            block.instructions.indexOfFirst {
                                it !is Phi && it is AssignInstruction && it.lhs.toString() == variable ||
                                        it is BranchInstruction && run {
                                            val branch = it
                                            val destBlock = blocks.first { it.label == branch.label }
                                            destBlock.instructions
                                                    .filterIsInstance<Phi>()
                                                    .any { "${it.lhs}" == variable }
                                        }
                            }.let { if (it == -1) 0 else it }
                    },
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
                                                        it.second is Variable && "${it.second}" == variable
                                                    } != null
                                                }
                                    }
                        }
                    else
                        block.instructions.size,
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

fun dummyColorGraph(
        nodes: List<Pair<String, Type>>,
        colors: List<InRegister>,
        initialColoring: Map<String, Coloring>,
        usedStackOffset: Int,
        conflictGraph: Graph,
        coloringPreferences: Map<String, Set<ColoringPreference>>,
        blocks: List<Block>
): VariableAssignment {
    val coloring = hashMapOf<String, StaticAssemblyValue>()
    var maxStackOffset = usedStackOffset
    val remainColors = colors.toHashSet()

    for ((_, blockColoring) in initialColoring) {
        for ((node, color) in blockColoring) {
            coloring[node] = color
            remainColors.remove(color)
            if (color is InStack)
                maxStackOffset = maxOf(maxStackOffset, color.offset)
        }
    }
    for ((_, preferences) in coloringPreferences) {
        for (preference in preferences) {
            if (preference is Predefined) {
                coloring[preference.node] = preference.color
                remainColors.remove(preference.color)
                if (preference.color is InStack)
                    maxStackOffset = maxOf(maxStackOffset, preference.color.offset)
            }   
        }
    }

    for ((node, type) in nodes) {
        if (node !in coloring) {
            if (remainColors.isNotEmpty()) {
                // TODO [NOT_CORRECT] big nodes (type.size > 8) need more than one register
                val color = remainColors.first().ofSize(type.size)
                coloring[node] = color
                remainColors.remove(color)
            } else {
                maxStackOffset += type.size
                coloring[node] = InStack(maxStackOffset, type.size)
            }
        }
    }

    println("RAPKA_COLORING: $coloring")
    return blocks.asSequence()
            .map { it.label to coloring }
            .toMap()
}