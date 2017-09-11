package moklev.asm.utils

import moklev.asm.compiler.IntArgumentsAssignment
import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.compiler.SystemVFunctionArgumentsLoader
import moklev.utils.ASMBuilder
import java.util.*

/**
 * @author Vyacheslav Moklev
 */

fun compileAssign(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    if (lhs != rhs) {
        when {
            lhs is InStack && rhs is InStack -> {
                // TODO get temp register
                val tempRegister = R15
                builder.appendLine("mov", tempRegister, "$rhs")
                builder.appendLine("mov", "$lhs", tempRegister)
            }
            lhs is InStack && rhs is StackAddrVariable -> {
                // TODO get temp register
                val tempRegister = R15
                builder.appendLine("lea", tempRegister, "$rhs")
                builder.appendLine("mov", "$lhs", tempRegister)
            }
            lhs is InStack && (rhs is Int64Const || rhs is InRegister) -> {
                // TODO must be typed or at least sized
                builder.appendLine("mov", lhs, rhs)
            }
            lhs is InRegister && (rhs is InStack || rhs is InRegister || rhs is Int64Const) -> {
                builder.appendLine("mov", lhs, rhs)
            }
            lhs is InRegister && rhs is StackAddrVariable -> {
                builder.appendLine("lea", lhs, rhs)
            }
            else -> error("Not supported: assign(${lhs.javaClass}, ${rhs.javaClass})")
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

//    val intArguments = args
//            .asSequence()
//            .filter { it.first == Type.INT }
//            .map { it.second }
//            .toList()
//
//    val reassignmentList = arrayListOf<Pair<StaticAssemblyValue, StaticAssemblyValue>>()
//    intArguments.forEachIndexed { i, arg ->
//        val dest = IntArgumentsAssignment[i]
//        if (dest is InRegister)
//            reassignmentList.add(arg.value(localAssignment)!! to dest)
//    }
//    var pushedSize = 0
//    for (i in (0 until intArguments.size).reversed()) {
//        val dest = IntArgumentsAssignment[i]
//        if (dest is InStack) {
//            builder.appendLine("push", intArguments[i].value(localAssignment)!!)
//            pushedSize += 8 // TODO always 8?
//        }
//    }
//
//    compileReassignment(builder, reassignmentList)

    println("KOKOS: $funcName")
    val pushedSize = SystemVFunctionArgumentsLoader.pushArguments(
            builder, 
            args.map { it.second.value(localAssignment)!! }
    )
    
    builder.appendLine("call", funcName)
    if (pushedSize > 0)
        builder.appendLine("add", RSP, pushedSize)
    if (result != null)
        compileAssign(builder, result, RAX)

    for (register in registersToSave.asReversed()) {
        compilePop(builder, register)
    }
}

fun compileBinaryOperation(builder: ASMBuilder,
                           operation: String,
                           lhs: StaticAssemblyValue,
                           rhs1: StaticAssemblyValue,
                           rhs2: StaticAssemblyValue,
                           symmetric: Boolean = false,
                           imm32Only: Boolean = true,
                           swapped: Boolean = false) {
    if (imm32Only && rhs2 is Int64Const && rhs2.value.toInt().toLong() != rhs2.value) {
        val tempRegister = if (lhs == rhs1) R15 else lhs
        compileAssign(builder, tempRegister, rhs2)
        if (symmetric)
            builder.appendLine(operation, tempRegister, rhs1)
        else
            error("Operation is not supported: $operation, $lhs, $rhs1, $rhs2")
        compileAssign(builder, lhs, tempRegister)
        return
    }

    if (symmetric && !swapped) {
        if (lhs == rhs2 && lhs != rhs1
                || rhs1 is InStack && rhs2 is InRegister
                || rhs1 is Int64Const && rhs2 !is Int64Const)
            return compileBinaryOperation(builder, operation, lhs, rhs2, rhs1, symmetric, imm32Only, swapped = true)
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
    if (lhs is InStack && rhs is InStack || lhs is Int64Const) {
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

    val raxUsed = rhs2 == RAX || liveRange
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
    else if (rhs2 == RAX)
        tempRegister2
    else if (rhs2 is Int64Const) {
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

    if (raxUsed && lhs1 != RAX && lhs2 != RAX)
        compileAssign(builder, RAX, tempRegister2)
    if (rdxUsed && lhs1 != RDX && lhs2 != RDX)
        compileAssign(builder, RDX, tempRegister)
}

fun compileStore(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    val tempRegister = R15
    val tempRegister2 = R14
    val actualRhs = if (rhs is InStack || rhs is StackAddrVariable) {
        compileAssign(builder, tempRegister, rhs)
        tempRegister
    } else rhs

    val actualLhs = if (lhs is InStack) {
        compileAssign(builder, tempRegister2, lhs)
        tempRegister2
    } else lhs
    
    if (actualLhs is StackAddrVariable) {
        builder.appendLine("mov", "qword $actualLhs", actualRhs)
    } else {
        builder.appendLine("mov", "qword [$actualLhs]", actualRhs)
    }
}