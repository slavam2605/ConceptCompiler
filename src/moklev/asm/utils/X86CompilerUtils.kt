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
            val tempRegister = R15
            builder.appendLine("mov", tempRegister, "$rhs")
            builder.appendLine("mov", "$lhs", tempRegister)
        } else if (lhs is InStack && rhs !is InRegister) {
            // TODO must be typed or at least sized
            builder.appendLine("mov", lhs, rhs)
        } else {
            builder.appendLine("mov", lhs, rhs)
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
            val tempRegister = R14
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
        RAX, RCX, RDX,
        RDI, RSI, RSP,
        R8, R9, R10, R11
)

fun compileCall(builder: ASMBuilder,
                funcName: String,
                args: List<Pair<Type, CompileTimeValue>>,
                variableAssignment: VariableAssignment,
                currentBlockLabel: String,
                liveRange: Map<String, LiveRange>,
                indexInBlock: Int,
                result: StaticAssemblyValue? = null,
                definingVariable: String? = null) {
    val localAssignment = variableAssignment[currentBlockLabel]!!
    val registersToSave = liveRange
            .asSequence()
            .filter { it.key != definingVariable }
            .filter { indexInBlock >= it.value.firstIndex && indexInBlock < it.value.lastIndex }
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

fun compileBinaryOperation(
        builder: ASMBuilder,
        operation: String,
        lhs: StaticAssemblyValue,
        rhs1: StaticAssemblyValue,
        rhs2: StaticAssemblyValue,
        symmetric: Boolean = false) {
    if (symmetric) {
        if (lhs == rhs2 && lhs != rhs1 
                || rhs1 is InStack && rhs2 is InRegister
                || rhs1 is IntConst && rhs2 !is IntConst)
            return compileBinaryOperation(builder, operation, lhs, rhs2, rhs1)
    }
    
    if (lhs != rhs1 && lhs == rhs2 || lhs is InStack) {
        // TODO proper temp register
        val tempRegister = R15
        compileAssign(builder, tempRegister, rhs1)
        builder.appendLine(operation, tempRegister, rhs2)
        compileAssign(builder, lhs, tempRegister)
        return
    }
    
    if (lhs != rhs1) 
        compileAssign(builder, lhs, rhs1)
    builder.appendLine(operation, lhs, rhs2)
}

fun compileCompare(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    if (lhs is InStack && rhs is InStack || lhs is IntConst) {
        // TODO normal temp register
        val tempRegister = R15
        compileAssign(builder, tempRegister, lhs)
        builder.appendLine("cmp", tempRegister, rhs)
        return
    }
    
    builder.appendLine("cmp", lhs, rhs)
}

fun compileDiv(
        builder: ASMBuilder,
        lhs1: StaticAssemblyValue?,
        lhs2: StaticAssemblyValue?,
        rhs1: StaticAssemblyValue,
        rhs2: StaticAssemblyValue,
        localAssignment: Map<String, StaticAssemblyValue>,
        liveRange: Map<String, LiveRange>,
        indexInBlock: Int,
        definingVariable: String? = null
) {
    val rdxUsed = rhs2 == RDX || liveRange
            .asSequence()
            .filter { it.key != definingVariable }
            .filter { indexInBlock >= it.value.firstIndex && indexInBlock < it.value.lastIndex }
            .mapNotNull { localAssignment[it.key] }
            .filterIsInstance<InRegister>()
            .any { it == RDX }

    val raxUsed = liveRange
            .asSequence()
            .filter { it.key != definingVariable }
            .filter { indexInBlock >= it.value.firstIndex && indexInBlock < it.value.lastIndex }
            .mapNotNull { localAssignment[it.key] }
            .filterIsInstance<InRegister>()
            .any { it == RAX }

    val tempRegister = R15 // TODO normal temp register
    val tempRegister2 = R14
    val tempRegister3 = R13
    
    if (raxUsed)
        compileAssign(builder, tempRegister2, RAX)
    if (rdxUsed)
        compileAssign(builder, tempRegister, RDX)
    compileAssign(builder, RAX, rhs1)

    val actualRhs2 = if (rhs2 == RDX)
        tempRegister
    else if (rhs2 is IntConst) {
        compileAssign(builder, tempRegister3, rhs2)
        tempRegister3
    } else
        rhs2

    builder.appendLine("cqo")
    builder.appendLine("idiv", actualRhs2)

    if (lhs1 != null)
        compileAssign(builder, lhs1, RAX)
    if (lhs2 != null)
        compileAssign(builder, lhs2, RDX)
    
    if (raxUsed)
        compileAssign(builder, RAX, tempRegister2)
    if (rdxUsed)
        compileAssign(builder, RDX, tempRegister)
}