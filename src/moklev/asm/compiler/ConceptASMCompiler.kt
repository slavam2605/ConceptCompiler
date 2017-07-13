package moklev.asm.compiler

import moklev.asm.instructions.ExternalAssign
import moklev.asm.interfaces.RawTextInstruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */

// TODO layout blocks to minimize amount of jumps
// TODO properly handle temp registers
// TODO support global variables
// TODO appropriate spill decisions (loop depth + precolored variables should be able to be spilled), set spill cost
// TODO eliminate blocks with optimized out instructions

const val startBlockLabel = ".func_start" 
const val endBlockLabel = ".func_end"

object IntArgumentsAssignment {
    operator fun get(index: Int): StaticAssemblyValue {
        return when (index) {
            0 -> RDI
            1 -> RSI
            2 -> RDX
            3 -> RCX
            4 -> R8
            5 -> R9
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
//            setOf("rdi", "rsi").map { InRegister(it) }, // TODO normal registers
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

    var stackUsed = false
    val registersToSave = HashSet<String>()
    for ((_, assignment) in variableAssignment) {
        for (register in calleeToSave) {
            val values = HashSet(assignment.values)
            if (register in values) {
                registersToSave.add(register.register)
            }
            if (values.firstOrNull { it is InStack } != null) {
                registersToSave.add("rbp")
                stackUsed = true
            }
        }
    }
    val registersToSaveOrdered = registersToSave.toList()
    val startBlock = blocks.first { it.label == startBlockLabel }
    val endBlock = blocks.first { it.label == endBlockLabel }
    if (stackUsed) {
        var maxOffset = 0
        variableAssignment.values.forEach {
            it.values.forEach {
                if (it is InStack) {
                    maxOffset = maxOf(maxOffset, it.offset)
                }
            }
        }
        maxOffset = maxOffset + (maxOffset and 15)
        if (maxOffset > 0) {
            startBlock.instructions.addFirst(RawTextInstruction("sub rsp, $maxOffset"))
        }
        startBlock.instructions.addFirst(RawTextInstruction("mov rbp, rsp"))
    }
    for (register in registersToSaveOrdered) {
        startBlock.instructions.addFirst(RawTextInstruction("push $register"))
    }
    for (register in registersToSaveOrdered.asReversed()) {
        endBlock.instructions.addFirst(RawTextInstruction("pop $register"))
    }
    if (stackUsed) {
        endBlock.instructions.addFirst(RawTextInstruction("mov rsp, rbp"))
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
