package moklev.asm.utils

import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SystemVFunctionArgumentsLoader
import java.util.*

/**
 * @author Vyacheslav Moklev
 */

fun compileAssign(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    if (lhs != rhs) {
        when {
            lhs is InStack && rhs is InStack -> {
                // TODO get temp register
                // TODO [NOT_CORRECT] for big sizes
                val tempRegister = R15(lhs.type)
                builder.appendLine("mov", tempRegister, "$rhs")
                builder.appendLine("mov", "$lhs", tempRegister)
            }
            lhs is InStack && rhs is StackAddrVariable -> {
                // TODO get temp register
                val tempRegister = R15(lhs.type)
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
            // TODO [REVIEW] what is reassignment of different size?
            val tempRegister = R14(Type.Int64)
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
).map { it(Type.Undefined) }

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

    println("KOKOS: $funcName")
    val pushedSize = SystemVFunctionArgumentsLoader.pushArguments(
            builder, 
            args.map { it.second.value(localAssignment)!! }
    )
    
    builder.appendLine("call", funcName)
    if (pushedSize > 0)
        builder.appendLine("add", RSP(Type.Undefined), pushedSize)
    if (result != null)
        // TODO [NOT_WORKING] if result.type.size > 8
        compileAssign(builder, result, RAX(result.type))

    for (register in registersToSave.asReversed()) {
        compilePop(builder, register)
    }
}

// TODO [REVIEW] totally review this
fun compileBinaryOperation(builder: ASMBuilder,
                           operation: String,
                           lhs: StaticAssemblyValue,
                           rhs1: StaticAssemblyValue,
                           rhs2: StaticAssemblyValue,
                           symmetric: Boolean = false,
                           imm32Only: Boolean = true,
                           swapped: Boolean = false) {
    // TODO [IMPROVE:CODE_GEN]
    val rhs1Temp = R14(rhs1.type)
    val rhs2Temp = R15(rhs2.type)
    val lhsTemp = R13(lhs.type)
    
    compileAssign(builder, rhs1Temp, rhs1)
    compileAssign(builder, rhs2Temp, rhs2)
    
    compileAssign(builder, lhsTemp, rhs1Temp)
    builder.appendLine(operation, lhsTemp, rhs2Temp)
    
    if (rhs1 !is ConstValue)
        compileAssign(builder, rhs1, rhs1Temp)
    if (rhs2 !is ConstValue)
        compileAssign(builder, rhs2, rhs2Temp)
    compileAssign(builder, lhs, lhsTemp)
    
//    if (imm32Only && rhs2 is Int64Const && rhs2.value.toInt().toLong() != rhs2.value) {
//        val tempRegister = if (lhs == rhs1 || lhs is InStack && rhs1 is InStack) R15(Type.Int64) else rhs1
//        compileAssign(builder, tempRegister, rhs1)
//        
//        compileAssign(builder, lhs, rhs2)
//        if (symmetric)
//            builder.appendLine(operation, lhs, tempRegister)
//        else
//            error("Operation is not supported: $operation, $lhs, $rhs1, $rhs2")
//        return
//    }
//
//    if (symmetric && !swapped) {
//        if (lhs == rhs2 && lhs != rhs1
//                || rhs1 is InStack && rhs2 is InRegister
//                || rhs1 is Int64Const && rhs2 !is Int64Const)
//            return compileBinaryOperation(builder, operation, lhs, rhs2, rhs1, symmetric, imm32Only, swapped = true)
//    }
//
//    if (lhs != rhs1 && lhs == rhs2 || lhs is InStack) {
//        // TODO proper temp register
//        // TODO [NOT_CORRECT] type
//        val tempRegister = R15(Type.Int64)
//        compileAssign(builder, tempRegister, rhs1)
//        builder.appendLine(operation, tempRegister, rhs2)
//        compileAssign(builder, lhs, tempRegister)
//        return
//    }
//
//    if (lhs != rhs1)
//        compileAssign(builder, lhs, rhs1)
//    builder.appendLine(operation, lhs, rhs2)
}

fun compileCompare(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    if (lhs is InStack && rhs is InStack || lhs is Int64Const) {
        // TODO normal temp register
        // TODO [NOT_CORRECT] type
        val tempRegister = R15(Type.Int64)
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
    val rdxUsed = rhs2 == RDX(Type.Undefined) || liveRange
            .asSequence()
            .filter { it.key != definingVariable }
            .filter { indexInBlock >= it.value.firstIndex && indexInBlock < it.value.lastIndex }
            .mapNotNull { localAssignment[it.key] }
            .filterIsInstance<InRegister>()
            .any { it == RDX(Type.Undefined) }

    val raxUsed = rhs2 == RAX(Type.Undefined) || liveRange
            .asSequence()
            .filter { it.key != definingVariable }
            .filter { indexInBlock >= it.value.firstIndex && indexInBlock < it.value.lastIndex }
            .mapNotNull { localAssignment[it.key] }
            .filterIsInstance<InRegister>()
            .any { it == RAX(Type.Undefined) }

    // TODO [NOT_CORRECT] type
    val tempRegister = R15(Type.Int64) // TODO normal temp register
    val tempRegister2 = R14(Type.Int64)
    val tempRegister3 = R13(Type.Int64)

    if (raxUsed)
        compileAssign(builder, tempRegister2, RAX(Type.Int64))
    if (rdxUsed)
        compileAssign(builder, tempRegister, RDX(Type.Int64))
    // TODO [REVIEW] why Type.Int64?
    compileAssign(builder, RAX(Type.Int64), rhs1)

    val actualRhs2 = if (rhs2 == RDX(Type.Int64))
        tempRegister
    else if (rhs2 == RAX(Type.Int64))
        tempRegister2
    else if (rhs2 is Int64Const) {
        compileAssign(builder, tempRegister3, rhs2)
        tempRegister3
    } else
        rhs2

    builder.appendLine("cqo")
    builder.appendLine("idiv", actualRhs2)

    // TODO [REVIEW] types
    if (lhs1 != null)
        compileAssign(builder, lhs1, RAX(Type.Int64))
    if (lhs2 != null)
        compileAssign(builder, lhs2, RDX(Type.Int64))

    if (raxUsed && lhs1 != RAX(Type.Int64) && lhs2 != RAX(Type.Int64))
        compileAssign(builder, RAX(Type.Int64), tempRegister2)
    if (rdxUsed && lhs1 != RDX(Type.Int64) && lhs2 != RDX(Type.Int64))
        compileAssign(builder, RDX(Type.Int64), tempRegister)
}

fun compileStore(builder: ASMBuilder, lhs: StaticAssemblyValue, rhs: StaticAssemblyValue) {
    // TODO [REVIEW] types
    val tempRegister = R15(Type.Int64)
    val tempRegister2 = R14(Type.Int64)
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