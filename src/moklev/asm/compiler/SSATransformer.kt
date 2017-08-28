package moklev.asm.compiler

import moklev.asm.instructions.*
import moklev.asm.interfaces.*
import moklev.asm.utils.*
import moklev.utils.ASMBuilder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

/**
 * @author Vyacheslav Moklev
 */
object SSATransformer {
    class Block(val label: String, val instructions: ArrayDeque<Instruction>) {
        val nextBlocks: MutableSet<Block> = mutableSetOf<Block>()
        val prevBlocks: MutableSet<Block> = mutableSetOf<Block>()
        val usedVariables = HashSet<String>()
        val localVariables = HashSet<String>()
        val lastVariableVersions = HashMap<String, Int>()
        var initStackOffset: Int = -1
        var maxStackOffset: Int = 0

        constructor(block: Block, instructions: ArrayDeque<Instruction> = ArrayDeque()) : this(block.label, instructions) {
            initStackOffset = block.initStackOffset
            maxStackOffset = block.maxStackOffset
        }

        fun addNextBlock(block: Block) {
            nextBlocks.add(block)
            block.prevBlocks.add(this)
        }

        override fun toString(): String {
            return "$label:\n${instructions.joinToString(separator = "") { "$it\n" }}"
        }

        fun compile(
                builder: ASMBuilder,
                blocks: Map<String, Block>,
                variableAssignment: VariableAssignment,
                nextBlockLabel: String?,
                liveRanges: Map<String, Map<String, LiveRange>>
        ) {
            val localAssignment = variableAssignment[label]!!
            println("KEYS[$label] = ${localAssignment.keys}")
            // TODO handle nextBlockLabel to avoid needless last jump to adjacent block
            val localLiveRange = liveRanges[label]!!
            builder.label(label)
            instructions.forEachIndexed { i, instruction ->
                builder.newLineComment(instruction)
                instruction.compile(builder, blocks, variableAssignment, label, localLiveRange, i)
            }
        }

        fun recalcUsedVariables() {
            usedVariables.clear()
            instructions
                    .asSequence()
                    .flatMap { it.usedValues.asSequence() }
                    .filterIsInstance<Variable>()
                    .mapTo(usedVariables) { "$it" }
            instructions
                    .asSequence()
                    .filterIsInstance<AssignInstruction>()
                    .mapTo(usedVariables) { "${it.lhs}" }
        }

        fun propagateInitStackOffset() {
            val blockMap = nextBlocks
                    .asSequence()
                    .map { it.label to it }
                    .toMap()
            var stackOffset = initStackOffset
            for (instruction in instructions) {
                if (instruction is StackAlloc)
                    stackOffset += instruction.size
                if (instruction is StackFree)
                    stackOffset -= instruction.size
                if (stackOffset > maxStackOffset)
                    maxStackOffset = stackOffset
                if (instruction is BranchInstruction) {
                    val block = blockMap[instruction.label]!!
                    if (block.initStackOffset < 0) {
                        block.initStackOffset = stackOffset
                    }
                    if (block.initStackOffset != stackOffset) {
                        error("Different offsets for block [${block.label}]: ${block.initStackOffset} and $stackOffset")
                    }
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as Block
            return label == other.label
        }

        override fun hashCode(): Int {
            return label.hashCode()
        }
    }

    fun transform(instructions: List<Instruction>, functionArguments: List<String>): Pair<Int, List<Block>> {
        val blocks = extractBlocks(instructions)
        val startBlock = Block(startBlockLabel, ArrayDeque())
        functionArguments.forEachIndexed { index, argument ->
            val newVar = Variable("$argument#var")
            startBlock.instructions.add(Assign(newVar, IntArgumentsAssignment[index])) // TODO index must be among int arguments, valid only for ints
        }
        functionArguments.forEachIndexed { index, argument ->
            val external = Variable(argument)
            val argVar = Variable("$argument#var")
            startBlock.instructions.add(StackAlloc(external, 8)) // TODO size of type
            startBlock.instructions.add(Store(external, argVar)) // TODO index must be among int arguments, valid only for ints
        }
        startBlock.instructions.add(Jump(blocks[0].label))
        val endBlock = Block(endBlockLabel, ArrayDeque())
        endBlock.instructions.add(RawTextInstruction("ret"))
        blocks.add(startBlock)
        blocks.add(endBlock)
        connectBlocks(blocks)

        blocks.forEach { println(it) }

        calcStackOffsets(blocks)

        for (block in blocks) {
            println("BLOCK_STACK[${block.label}] = ${block.initStackOffset}")
        }

        var maxStackOffset = 0
        for (block in blocks) {
            val newInstructions = arrayListOf<Instruction>()
            var currentStackOffset = block.initStackOffset
            for (instruction in block.instructions) {
                if (instruction is StackAlloc) {
                    currentStackOffset += instruction.size
                    newInstructions.add(Assign(
                            instruction.lhs,
                            StackAddrVariable(currentStackOffset)
                    ))
                } else if (instruction !is StackFree) {
                    newInstructions.add(instruction)
                }
            }
            block.instructions.clear()
            block.instructions.addAll(newInstructions)
            maxStackOffset = maxOf(maxStackOffset, currentStackOffset)
        }

        val nonLocalVariables = defineNonLocalVariables(blocks)

        println(nonLocalVariables)

        // TODO KEKERIS

        println("ANAL: ")
        performPointerAnalysis(blocks).let {
            it.pointsTo.forEach { node, set ->
                println("$node -> $set")
            }
        }

        return maxStackOffset to blocks
    }

    private fun calcStackOffsets(blocks: List<Block>) {
        val used = hashSetOf(startBlockLabel)
        val startBlock = blocks.first { it.label == startBlockLabel }
        startBlock.initStackOffset = 0
        val queue = ArrayDeque<Block>()
        queue.addLast(startBlock)
        while (queue.isNotEmpty()) {
            val block = queue.pollFirst()
            block.propagateInitStackOffset()
            for (nextBlock in block.nextBlocks) {
                if (nextBlock.label !in used) {
                    used.add(nextBlock.label)
                    queue.addLast(nextBlock)
                }
            }
        }
    }

    private fun defineNonLocalVariables(blocks: MutableList<Block>): Collection<String> {
        val variables = LinkedHashSet<String>()
        for (block in blocks) {
            block.recalcUsedVariables()
            variables.addAll(block.usedVariables)
        }
        val localVariables = HashSet<String>()
        for (variable in variables) {
            val holders = blocks.filter { it.usedVariables.contains(variable) }
            if (holders.size == 1) {
                holders[0].localVariables.add(variable)
                localVariables.add(variable)
            }
        }
        return variables - localVariables
    }

    private fun extractBlocks(instructions: List<Instruction>): MutableList<Block> {
        var currentList = ArrayDeque<Instruction>()
        var lastLabel = StaticUtils.nextLabel()
        val blocks = mutableListOf<Block>()
        var tempLabel = 0
        var finishBlock = false
        for (instruction in instructions) {
            when (instruction) {
                is Label -> {
                    if (currentList.isNotEmpty()) {
                        val currentLast = currentList.last
                        if (currentLast !is UnconditionalBranchInstruction) {
                            currentList.add(Jump(instruction.name))
                        }
                        blocks.add(Block(lastLabel, currentList))
                        currentList = ArrayDeque<Instruction>()
                    }
                    lastLabel = instruction.name
                    finishBlock = false
                }
                is BranchInstruction -> {
                    finishBlock = true
                    currentList.add(instruction)
                }
                else -> {
                    if (finishBlock) {
                        val newLabel = ".TL$tempLabel"
                        tempLabel++
                        val currentLast = currentList.last
                        if (currentLast !is UnconditionalBranchInstruction) {
                            currentList.add(Jump(newLabel))
                        }
                        blocks.add(Block(lastLabel, currentList))
                        currentList = ArrayDeque<Instruction>()
                        lastLabel = newLabel
                        finishBlock = false
                    }
                    currentList.add(instruction)
                }
            }
        }
        blocks.add(Block(lastLabel, currentList))
        for (block in blocks) {
            val firstUnconditionalBranchIndex = block.instructions
                    .mapIndexed { index, instruction -> index to instruction }
                    .first { (_, instruction) -> instruction is UnconditionalBranchInstruction }
                    .first
            val toDrop = block.instructions.size - firstUnconditionalBranchIndex - 1
            for (counter in 0..toDrop - 1) {
                block.instructions.removeLast()
            }
        }
        return blocks
    }

    private fun connectBlocks(blocks: List<Block>) {
        val blockMap = HashMap<String, Block>()
        for (block in blocks) {
            blockMap[block.label] = block
        }
        for (block in blocks) {
            block.instructions
                    .asSequence()
                    .filterIsInstance<BranchInstruction>()
                    .forEach { block.addNextBlock(blockMap[it.label]!!) }
        }
    }

    fun propagateConstants(blocks: List<Block>): Pair<Boolean, List<Block>> {
        val assignMap = HashMap<Variable, CompileTimeValue>()
        blocks
                .flatMap { it.instructions }
                .filterIsInstance<Assign>()
                .filter { it.rhs1 is ConstValue }
                .forEach { assignMap[it.lhs] = assignMap[it.rhs1] ?: it.rhs1 }
        val rightUsedVariables = HashSet<String>()
        blocks
                .asSequence()
                .flatMap {
                    it.instructions
                            .asSequence()
                            .filter { it !is Phi }
                }
                .flatMapTo(rightUsedVariables) {
                    it.usedValues
                            .asSequence()
                            .filterIsInstance<Variable>()
                            .map { "$it" }
                }
        blocks
                .asSequence()
                .flatMap {
                    it.instructions
                            .asSequence()
                            .filterIsInstance<Phi>()
                }
                .flatMapTo(rightUsedVariables) {
                    val lhsName = "${it.lhs}"
                    it.pairs
                            .asSequence()
                            .map { it.second }
                            .filterIsInstance<Variable>()
                            .map { "$it" }
                            .filter { it != lhsName }
                }

        val result = ArrayList<Block>()
        var eliminated = false
        for (block in blocks) {
            val newBlock = Block(block)
            for (instruction in block.instructions) {
                if (instruction is AssignInstruction && "${instruction.lhs}" !in rightUsedVariables) {
                    eliminated = true
                    continue
                }
                var newInstruction = instruction
                for ((key, value) in assignMap) {
                    newInstruction = newInstruction.substitute(key, value)
                }
                newBlock.instructions.add(newInstruction)
            }
            result.add(newBlock)
        }
        return (assignMap.isNotEmpty() || eliminated) to result
    }

    fun simplifyInstructions(blocks: List<Block>): List<Block> {
        val result = ArrayList<Block>()
        blocks.mapTo(result) { block ->
            Block(
                    block,
                    ArrayDeque(block.instructions.flatMap { it.simplify() })
            )
        }
        return result
    }

    fun recalcBlockConnectivity(blocks: List<Block>, startBlockLabel: String): List<Block> {
        for (block in blocks) {
            block.nextBlocks.clear()
            block.prevBlocks.clear()
        }
        connectBlocks(blocks)

        val queue = ArrayDeque<Block>()
        val seen = LinkedHashSet<String>()
        val startBlock = blocks.first { it.label == startBlockLabel }
        queue.add(startBlock)
        seen.add(startBlock.label)
        val newBlocks = ArrayList<Block>()
        while (queue.isNotEmpty()) {
            val block = queue.poll()
            val prevBlocks = block.prevBlocks.asSequence()
                    .map { it.label }
                    .toHashSet()
            val newInstructions = ArrayDeque<Instruction>()
            for (instruction in block.instructions) {
                if (instruction is Phi) {
                    newInstructions.add(Phi(
                            instruction.lhs,
                            instruction.pairs.filter { it.first in prevBlocks }
                    ))
                } else {
                    newInstructions.add(instruction)
                }
            }
            newBlocks.add(Block(block, newInstructions))
            for (nextBlock in block.nextBlocks) {
                if (nextBlock.label !in seen) {
                    queue.add(nextBlock)
                    seen.add(nextBlock.label)
                }
            }
        }
        connectBlocks(newBlocks)
        return newBlocks
    }

    fun performOptimizations(blockList: List<Block>): List<Block> {
        var blocks = blockList
        while (true) {
            println("\n========= Transformed SSA code ========\n")

            for (block in blocks) {
                println("${block.label}:")
                for (instruction in block.instructions) {
                    println("$instruction")
                }
                println()
            }

            println("\n^^^^^^^^^ End of code ^^^^^^^^^\n")
            
            var (changed, newBlocks) = propagateConstants(blocks)
            blocks = simplifyInstructions(newBlocks)
            blocks = recalcBlockConnectivity(blocks, startBlockLabel)
            blocks = eliminateStackVariables(blocks)
            
            changed = changed || !compareInstructionSet(blocks, newBlocks)
            if (!changed) {
                break
            }
        }
        blocks.forEach { it.recalcUsedVariables() }
        return blocks
    }

    private fun eliminateStackVariables(blocks: List<Block>): List<Block> {
        val used = hashSetOf<StackAddrVariable>()
        val rawUsed = hashSetOf<StackAddrVariable>()

        blocks.forEach { block ->
            block.instructions.forEach { instruction ->
                val allUsed = instruction.usedValues.filterIsInstance<StackAddrVariable>()
                used.addAll(allUsed)
                if (instruction is MemoryInstruction) {
                    rawUsed.addAll(instruction.notMemoryUsed.filterIsInstance<StackAddrVariable>())
                } else {
                    rawUsed.addAll(allUsed)
                }
            }
        }

        used.removeAll(rawUsed)
        if (used.isEmpty()) {
            return blocks
        }
        
        val tempVarRegex = "#st_var([0-9]+)".toRegex()
        val maxUsedIndex = blocks
                .asSequence()
                .flatMap { it.instructions.asSequence() }
                .flatMap { it.usedValues.asSequence() }
                .filterIsInstance<Variable>()
                .mapNotNull {
                    tempVarRegex.find(it.name)
                            ?.groupValues
                            ?.get(1)
                            ?.toInt()
                }.max() ?: -1
        val newName = used.mapIndexed { index, stackVar ->
            stackVar to "#st_var${maxUsedIndex + index + 1}"
        }.toMap()
        val newBlocks = arrayListOf<Block>()
        for (block in blocks) {
            val newInstructions = ArrayDeque<Instruction>()
            for (instruction in block.instructions) {
                if (instruction is Store && instruction.lhsAddr is StackAddrVariable
                        && instruction.lhsAddr in used) {
                    newInstructions.add(Assign(Variable(newName[instruction.lhsAddr]!!), instruction.rhs))
                } else if (instruction is Load && instruction.rhsAddr is StackAddrVariable
                        && instruction.rhsAddr in used) {
                    newInstructions.add(Assign(instruction.lhs, Variable(newName[instruction.rhsAddr]!!)))
                } else {
                    newInstructions.add(instruction)
                }
            }
            newBlocks.add(Block(block, newInstructions))
        }
        return partlyTransformToSSA(newBlocks)
    }

    private fun partlyTransformToSSA(blocks: List<Block>): List<Block> {
        val countMap = hashMapOf<String, Int>()
        blocks.forEach { block ->
            block.instructions.forEach { instruction ->
                if (instruction is AssignInstruction) {
                    countMap.compute(instruction.lhs.toString()) { _, old -> (old ?: 0) + 1 }
                }
            }
        }
        val toTransform = countMap.filter { it.value > 1 }.keys
        // TODO save information about prev/next blocks
        return transformToSSA(toTransform, recalcBlockConnectivity(blocks, startBlockLabel))
    }
    
    private fun transformToSSA(transformVariables: Collection<String>, blocks: List<Block>): List<Block> {
        val lastVersionMap = HashMap<String, Int>()
        for (block in blocks) {
            for (variable in transformVariables) {
                val newVersion = lastVersionMap.compute(variable) { _, v -> 1 + (v ?: -1) }!!
                block.instructions.addFirst(Assign(Variable(variable, newVersion), Undefined))
            }
            for (instruction in block.instructions) {
                instruction.usedValues
                        .asSequence()
                        .filterIsInstance<Variable>()
                        .forEach { it.version = lastVersionMap[it.name]!! }
                if (instruction is AssignInstruction) {
                    val variable = instruction.lhs
                    val newVersion = lastVersionMap.compute(variable.name) { _, v -> 1 + (v ?: -1) }!!
                    variable.version = newVersion
                }
            }
            for (variable in transformVariables) {
                val version = lastVersionMap[variable] ?: error("Unknown transform variable")
                block.lastVariableVersions[variable] = version
            }
        }

        for (block in blocks) {
            val list = mutableListOf<Instruction>()
            for (instruction in block.instructions) {
                if (instruction is Assign && instruction.rhs1 is Undefined) {
                    val phiArgs = block.prevBlocks
                            .map { it.label to Variable(instruction.lhs.name, it.lastVariableVersions[instruction.lhs.name]!!) }
                    if (phiArgs.isNotEmpty()) {
                        list.add(Phi(instruction.lhs, phiArgs))
                    }
                } else {
                    list.add(instruction)
                }
            }
            block.instructions.clear()
            block.instructions.addAll(list)
        }
        return blocks
    }
    
    private fun compareInstructionSet(blocks: List<SSATransformer.Block>, newBlocks: List<SSATransformer.Block>): Boolean {
        val list1 = blocks.flatMap { it.instructions }
        val list2 = newBlocks.flatMap { it.instructions }
        if (list1.size != list2.size)
            return false
        return (0..list1.size - 1).none { list1[it].toString() != list2[it].toString() }
    }
}