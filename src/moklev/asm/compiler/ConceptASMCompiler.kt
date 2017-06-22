package moklev.asm.compiler

import moklev.asm.interfaces.ExternalAssign
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */

// TODO layout blocks to minimize amount of jumps 
// TODO advanced coloring with regard to cycles
// TODO support allocation in stack
// TODO properly handle temp registers
// TODO support global variables

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

    val commonAssignment = colorGraph(
            setOf("rax", "rbx", "rcx", "rdx", "r8", "r9", "r10", "r11").map { InRegister(it) }.toSet(), // TODO normal registers
            intArguments.mapIndexed { i, s -> externalNames[s]!! to IntArgumentsAssignment[i] }.toMap(), 
            conflictGraph
    )

    println(commonAssignment)

    val variableAssignment = blocks
            .asSequence()
            .map { it.label to commonAssignment }
            .toMap()
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
