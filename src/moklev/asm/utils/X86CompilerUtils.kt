package moklev.asm.utils

import moklev.asm.compiler.IntArgumentsAssignment
import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.utils.ASMBuilder
import java.util.*

/**
 * @author Vyacheslav Moklev
 */

fun compileAssign(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    if (lhs != rhs) {
        if (lhs is InStack && rhs is InStack) {
            // TODO get temp register
            val tempRegister = "r15"
            builder.appendLine("mov", tempRegister, "$rhs")
            builder.appendLine("mov", "$lhs", tempRegister)
        } else if (lhs is InStack && rhs !is InRegister) {
            // TODO must be typed or at least sized
            builder.appendLine("mov", "qword $lhs", "$rhs")
        } else {
            builder.appendLine("mov", "$lhs", "$rhs")
        }
    }
}

fun compileReassignment(builder: ASMBuilder, assignList: List<Pair<StaticAssemblyValue, StaticAssemblyValue>>) {
    // Each memory location should be assigned only once, so in `backAssignGraph` 
    // for every node deg_out = 1, so map `StaticAssemblyValue → StaticAssemblyValue` is enough
    val backAssignGraph = HashMap<StaticAssemblyValue, StaticAssemblyValue>()
    val inputDegree = HashMap<StaticAssemblyValue, Int>()
    val noInputDegreeList = ArrayDeque<StaticAssemblyValue>()
    for ((src, dest) in assignList) {
        if (src == dest)
            continue
        backAssignGraph[dest] = src
        inputDegree.compute(src) { _, v -> (v ?: 0) + 1 }
        inputDegree.compute(dest) { _, v -> v ?: 0 }
    }
    inputDegree
            .filter { (_, deg) -> deg == 0 }
            .mapTo(noInputDegreeList) { it.key }

    while (noInputDegreeList.isNotEmpty()) {
        val dest = noInputDegreeList.pollFirst()
        val src = backAssignGraph[dest] ?: continue
        compileAssign(builder, dest, src)
        backAssignGraph.remove(dest)
        val newDegree = inputDegree.compute(src) { _, v -> v!! - 1 }!!
        if (newDegree == 0) {
            noInputDegreeList.addLast(src)
        }
    }

    val assigned = HashSet<StaticAssemblyValue>()
    for (dest in backAssignGraph.keys) {
        if (dest !in assigned) {
            // TODO properly choose temp register
            val tempRegister = InRegister("r14")
            compileAssign(builder, tempRegister, dest)
            var currentDest = dest
            while (true) {
                assigned.add(currentDest)
                val src = backAssignGraph[currentDest]!!
                if (src == dest) {
                    compileAssign(builder, currentDest, tempRegister)
                    break
                }
                compileAssign(builder, currentDest, src)
                currentDest = src
            }
        }
    }
}

fun compilePush(builder: ASMBuilder, value: StaticAssemblyValue) {
    when (value) {
        is InRegister -> {
            builder.appendLine("push", value.toString())
        }
        else -> NotImplementedError()
    }
}

fun compilePop(builder: ASMBuilder, value: StaticAssemblyValue) {
    when (value) {
        is InRegister -> {
            builder.appendLine("pop", value.toString())
        }
        else -> NotImplementedError()
    }
}

val callerToSave = listOf(
        "rax", "rcx", "rdx",
        "rdi", "rsi", "rsp",
        "r8", "r9", "r10", "r11"
).map { InRegister(it) }

fun compileCall(builder: ASMBuilder,
                funcName: String,
                args: List<Pair<Type, CompileTimeValue>>,
                variableAssignment: VariableAssignment,
                currentBlockLabel: String,
                liveRange: Map<String, LiveRange>,
                indexInBlock: Int,
                result: StaticAssemblyValue? = null) {
    val localAssignment = variableAssignment[currentBlockLabel]!!
    val registersToSave = liveRange
            .asSequence()
            .filter { indexInBlock > it.value.firstIndex && indexInBlock < it.value.lastIndex }
            .map { localAssignment[it.key]!! }
            .filterIsInstance<InRegister>()
            .filter { it in callerToSave }
            .toList()

    for (register in registersToSave) {
        compilePush(builder, register)
    }

    val intArguments = args
            .asSequence()
            .filter { it.first == Type.INT }
            .map { it.second }

    intArguments.forEachIndexed { i, arg ->
        compileAssign(builder, IntArgumentsAssignment[i], arg.value(localAssignment)!!)
    }

    builder.appendLine("call", funcName)
    if (result != null) {
        compileAssign(builder, result, RAX)
    }

    for (register in registersToSave.asReversed()) {
        compilePop(builder, register)
    }
}