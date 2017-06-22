package moklev.asm.compiler

import moklev.asm.instructions.Assign
import moklev.asm.instructions.Jump
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
        val nextBlocks = mutableSetOf<Block>()
        val prevBlocks = mutableSetOf<Block>()
        val usedVariables = HashSet<String>()
        val localVariables = HashSet<String>()
        val lastVariableVersions = HashMap<String, Int>()

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
            // TODO handle nextBlockLabel to avoid needless last jump to adjacent block
            val localLiveRange = liveRanges[label]!!
            builder.appendLine("$label:")
            instructions.forEachIndexed { i, instruction ->
                instruction.compile(builder, blocks, variableAssignment, label, localLiveRange, i)
            }
        }
    }

    fun transform(instructions: List<Instruction>, functionArguments: List<String>): List<Block> {
        val blocks = extractBlocks(instructions)
        val startBlock = Block("func_start", ArrayDeque())
        for (argument in functionArguments) {
            startBlock.instructions.add(ExternalAssign(Variable(argument)))
        }
        startBlock.instructions.add(Jump(blocks[0].label))
        val endBlock = Block("func_end", ArrayDeque())
        endBlock.instructions.add(NoArgumentsInstruction("ret"))
        blocks.add(startBlock)
        blocks.add(endBlock)
        connectBlocks(blocks)

//        println(detectLoops(blocks))
        
        for (block in blocks) {
            println("${block.label} => ${block.nextBlocks.joinToString { it.label }}")
        }

        blocks.forEach { println(it) }

        val nonLocalVariables = defineNonLocalVariables(blocks)

        println(nonLocalVariables)

        val lastVersionMap = HashMap<String, Int>()
        for (block in blocks) {
            for (variable in nonLocalVariables) {
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
            for (variable in nonLocalVariables) {
                val version = lastVersionMap[variable] ?: error("Unknown non-local variable")
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

    private fun defineNonLocalVariables(blocks: MutableList<Block>): Collection<String> {
        val variables = LinkedHashSet<String>()
        for (block in blocks) {
            val variablesInBlock =
                    block.instructions
                            .asSequence()
                            .flatMap { it.usedValues.asSequence() }
                            .filterIsInstance<Variable>()
                            .map { it.name }
                            .toMutableList()
            block.instructions
                    .asSequence()
                    .filterIsInstance<AssignInstruction>()
                    .mapTo(variablesInBlock) { it.lhs.name }
            block.usedVariables.addAll(variablesInBlock)
            variables.addAll(variablesInBlock)
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
        var lastLabel = "L0"
        val blocks = mutableListOf<Block>()
        for (instruction in instructions) {
            when (instruction) {
                is Label -> {
                    if (currentList.isNotEmpty()) {
                        blocks.add(Block(lastLabel, currentList))
                        currentList = ArrayDeque<Instruction>()
                    }
                    lastLabel = instruction.name
                }
                else -> {
                    currentList.add(instruction)
                }
            }
        }
        if (currentList.isNotEmpty()) {
            blocks.add(Block(lastLabel, currentList))
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
                .forEach { assignMap[it.lhs] = it.rhs1 }
        val result = ArrayList<Block>()
        for (block in blocks) {
            val newBlock = Block(block.label, ArrayDeque())
            for (instruction in block.instructions) {
                if (instruction is Assign && assignMap.containsKey(instruction.lhs)) {
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
        return assignMap.isNotEmpty() to result
    }

    fun simplifyInstructions(blocks: List<Block>): List<Block> {
        val result = ArrayList<Block>()
        blocks.mapTo(result) { block ->
            Block(
                    block.label,
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
            newBlocks.add(Block(block.label, newInstructions))
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
        // TODO eliminate unused variables (x = arg1 + arg2, no usage of x later)
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
            
            var (changed, newBlocks) = SSATransformer.propagateConstants(blocks)
            blocks = SSATransformer.simplifyInstructions(newBlocks)
            // TODO smarter set start block
            blocks = SSATransformer.recalcBlockConnectivity(blocks, "func_start")
            changed = changed || !compareInstructionSet(blocks, newBlocks)
            if (!changed) {
                break
            }
        }
        return blocks.toList()
    }

    private fun compareInstructionSet(blocks: List<SSATransformer.Block>, newBlocks: List<SSATransformer.Block>): Boolean {
        val list1 = blocks.flatMap { it.instructions }
        val list2 = newBlocks.flatMap { it.instructions }
        if (list1.size != list2.size)
            return false
        return (0..list1.size - 1).none { list1[it].toString() != list2[it].toString() }
    }
}