package moklev.asm.compiler

import moklev.asm.interfaces.ExternalAssign
import moklev.asm.interfaces.NoArgumentsInstruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */

// TODO layout blocks to minimize amount of jumps
// TODO properly handle temp registers
// TODO support global variables
// TODO add subsumption of some kind
// TODO appropriate spill decisions (loop depth + precolored variables should be able to be spilled), set spill cost
// TODO eliminate blocks with optimized out instructions

const val startBlockLabel = ".func_start" 
const val endBlockLabel = ".func_end"

object IntArgumentsAssignment {
    operator fun get(index: Int): StaticAssemblyValue {
        return when (index) {
            0 -> InRegister("rdi")
            1 -> InRegister("rsi")
            2 -> InRegister("rdx")
            3 -> InRegister("rcx")
            4 -> InRegister("r8")
            5 -> InRegister("r9")
            else -> InStack(8 * (index - 5))
        }
    }
}

val calleeToSave = listOf(
        "rbp", "rbx", "r12", "r13", "r14", "r15"
).map { InRegister(it) }

fun <A : Appendable> ASMFunction.compileTo(dest: A): A {
    dest.appendLine("global $name")
    dest.appendLine("$name:")

    val blocks = SSATransformer.performOptimizations(
            SSATransformer.transform(instructions, arguments.map { it.second })
    )
    
    val externalNames = HashMap<String, String>()
    for (block in blocks) {
        for (instruction in block.instructions) {
            if (instruction is ExternalAssign) {
                externalNames[instruction.lhs.name] = instruction.lhs.toString()
            }
        }
    }

    println("\n========= Transformed SSA code ========\n")

    for (block in blocks) {
        println("${block.label}:")
        for (instruction in block.instructions) {
            println("$instruction")
        }
    }

    println("\n^^^^^^^^^ End of code ^^^^^^^^^\n")

    val liveRanges = detectLiveRange(blocks)
    val conflictGraph = buildConflictGraph(liveRanges)
    val coalescingEdges = blocks
            .asSequence()
            .map {
                it.label to it.instructions
                        .asSequence()
                        .flatMap { it.coalescingEdges().asSequence() }
                        .toSet()
            }
            .toMap()
    
    val intArguments = arguments
            .asSequence()
            .filter { it.first == Type.INT }
            .map { it.second }
            .toList()

    println("conflictGraph = $conflictGraph")
    
    val variableAssignment = advancedColorGraph(
            setOf("rax", "rbx", "rcx", "rdx", "r8", "r9", "r10", "r11").map { InRegister(it) }, // TODO normal registers
            mapOf(
                    startBlockLabel to intArguments.mapIndexedNotNull { i, s ->
                            val name = externalNames[s] ?: return@mapIndexedNotNull null
                            name to IntArgumentsAssignment[i] 
                        }.toMap() 
            ),
            conflictGraph,
            coalescingEdges,
            blocks
    )

    for ((a, b) in variableAssignment) {
        println("$a: $b")
    }

    val registersToSave = HashSet<String>()
    for ((_, assignment) in variableAssignment) {
        for (register in calleeToSave) {
            val values = HashSet(assignment.values)
            if (register in values) {
                registersToSave.add(register.register)
            }
            if (values.firstOrNull { it is InStack } != null)
                registersToSave.add("rbp")
        }
    }
    val registersToSaveOrdered = registersToSave.toList()
    val startBlock = blocks.first { it.label == startBlockLabel }
    val endBlock = blocks.first { it.label == endBlockLabel }
    for (register in registersToSaveOrdered) {
        startBlock.instructions.addFirst(NoArgumentsInstruction("push $register"))
    }
    for (register in registersToSaveOrdered.asReversed()) {
        endBlock.instructions.addFirst(NoArgumentsInstruction("pop $register"))
    }
    
    val builder = ASMBuilder()
    for (i in 0..blocks.size - 1) {
        val block = blocks[i]
        val nextBlockLabel = blocks.getOrNull(i + 1)?.label
        block.compile(
                builder,
                blocks
                        .asSequence()
                        .map { it.label to it }
                        .toMap(),
                variableAssignment,
                nextBlockLabel,
                liveRanges
                        .asSequence()
                        .map { it.first.label to it.second }
                        .toMap()
        )
    }
    dest.append(builder.build())
    return dest
}

fun ASMFunction.compile(): String {
    return compileTo(StringBuilder()).toString()
}

private fun <A : Appendable> A.appendLine(x: Any?): Appendable {
    return append(x.toString()).append('\n')
}
