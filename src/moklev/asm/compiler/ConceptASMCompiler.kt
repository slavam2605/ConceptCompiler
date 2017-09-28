package moklev.asm.compiler

import moklev.asm.instructions.ExternalAssign
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.RawTextInstruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder
import java.util.*

/**
 * @author Moklev Vyacheslav
 */

// TODO layout blocks to minimize amount of jumps
// TODO properly handle temp registers
// TODO support global variables
// TODO appropriate spill decisions (loop depth + precolored variables should be able to be spilled), set spill cost
// TODO eliminate blocks with optimized out instructions
// TODO detect if execution path exists such that reaches no return statement
// TODO conflict graph computation should involve pointer analysis:
// a = stack_alloc 8 : a = addr <rbp - 8>
// b = stack_alloc 8 : b = addr <rbp - 16>
// store b, 42       : mov qword [rbp - 16], 42
// a = &b            : lea qword [rbp - 8], [rbp - 16]  
// c = &a            : c = addr <rbp - 8>
// d = load a        : if d is colored to <rbp - 8> then:
//                     mov qword [rbp - 8], [rbp - 8]
//                     <rbp - 8> is now 42 (value of b)           
// **c = 40 must be `b := 40`, but:
// e = load c        : mov e, [rbp - 8] => e = 42
// store e, 40       : mov 42, 40 => badoom!

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
            else -> InStack(-8 * (index - 4), 8) // TODO WAT
        }
    }
}

val calleeToSave = listOf(
        RBP, RBX, R12, R13, R14, R15
)

fun <A : Appendable> ASMFunction.compileTo(dest: A): A {
    dest.appendLine("global $name")
    dest.appendLine("$name:")

    val (maxStackOffset, blockList) = SSATransformer.transform(instructions, arguments)
    val blocks = SSATransformer.performOptimizations(blockList)
    
    val externalNames = HashMap<String, String>()
    for (block in blocks) {
        for (instruction in block.instructions) {
            if (instruction is ExternalAssign) {
                externalNames[instruction.lhs.name] = instruction.lhs.toString()
            }
        }
    }

    StaticUtils.state.clearVarTypes()
    StaticUtils.state.inferTypes(blocks.asSequence().flatMap { it.instructions.asSequence() }.asIterable())
    
    
    println("\n========= Transformed SSA code ========\n")

    for (block in blocks) {
        println("${block.label}:")
        for (instruction in block.instructions) {
            println("$instruction")
        }
    }

    println("\n^^^^^^^^^ End of code ^^^^^^^^^\n")

//    if (name != "index")
//        System.exit(0)
    
    val liveRanges = detectLiveRange(blocks)
    println("LIVE_KEKES: ${liveRanges.map { it.first.label to it.second }}")
    val conflictGraph = buildConflictGraph(liveRanges)
    val coalescingEdges = blocks
            .asSequence()
            .map {
                it.label to it.instructions
                        .asSequence()
                        .flatMap { it.coloringPreferences().asSequence() }
                        .toSet()
            }
            .toMap()
    
    val intArguments = arguments
            .asSequence()
            .filter { it.first == Type.Int64 }
            .map { it.second }
            .toList()

    println("conflictGraph = $conflictGraph")
    
    val nodes = blocks
            .flatMap { it.instructions }
            .flatMap { 
                val list = it.usedValues.toMutableList()
                if (it is AssignInstruction)
                    list.add(it.lhs)
                list
            }
            .map { it.toString() to it.type }
            .toSet()
            .toList()
    
//    val variableAssignment = advancedColorGraph(
    val variableAssignment = dummyColorGraph(
            nodes,
//            listOf(RAX, RBX, RCX, RDX, R8, R9, R10, R11), // TODO normal registers
            listOf(RDI, RSI, RDX, RCX, R8, R9, RAX, R10), // TODO normal registers
            mapOf(
                    startBlockLabel to intArguments.mapIndexedNotNull { i, s ->
                            val name = externalNames[s] ?: return@mapIndexedNotNull null
                            name to IntArgumentsAssignment[i] 
                        }.toMap() 
            ),
            maxStackOffset,
            conflictGraph,
            coalescingEdges,
            blocks
    )

    for ((a, b) in variableAssignment) {
        println("$a: $b")
    }

    var stackUsed = true // TODO must remove smarter: function call uses rbp too 
    val registersToSave = HashSet<String>()
    for (block in blocks) {
        println("BLOCK//${block.label}// -> ${block.maxStackOffset}")
        if (block.maxStackOffset > 0) {
            registersToSave.add("rbp")
            stackUsed = true
        }
    }
    for ((_, assignment) in variableAssignment) {
        for (register in calleeToSave) {
            val values = HashSet(assignment.values)
            if (register in values) {
                registersToSave.add(register.str) 
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
        blocks.forEach { 
            maxOffset = maxOf(maxOffset, it.maxStackOffset)
        }
        maxOffset += (maxOffset and 15)
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
    
    val builder = ASMBuilder(true)
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
