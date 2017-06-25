package moklev.asm.compiler

import moklev.asm.interfaces.ExternalAssign
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */

// TODO layout blocks to minimize amount of jumps
// TODO properly handle temp registers
// TODO support global variables
// TODO add subsumption of some kind
// TODO appropriate spill decisions (loop depth + precolored variables should be able to be spilled) 

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

fun <A : Appendable> ASMFunction.compileTo(dest: A): A {
    dest.appendLine("global $name")
    dest.appendLine("$name:")
    // TODO allocate arguments
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

    val intArguments = arguments
            .asSequence()
            .filter { it.first == Type.INT }
            .map { it.second }
            .toList()

    println("conflictGraph = $conflictGraph")
    
    val variableAssignment = advancedColorGraph(
            setOf("rax", "rbx", "rcx", "rdx", "r8", "r9", "r10", "r11").map { InRegister(it) }, // TODO normal registers
            mapOf(
                        "func_start" to intArguments.mapIndexedNotNull { i, s ->
                            val name = externalNames[s] ?: return@mapIndexedNotNull null
                            name to IntArgumentsAssignment[i] 
                        }.toMap() // TODO func_start rename or make a constant
            ),
            conflictGraph,
            blocks
    )

    for ((a, b) in variableAssignment) {
        println("$a: $b")
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
