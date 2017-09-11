package moklev.asm.compiler

import moklev.asm.instructions.Add
import moklev.asm.instructions.StackAlloc
import moklev.asm.instructions.Store
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder

/**
 * @author Moklev Vyacheslav
 */
// TODO add support of floating arguments
object SystemVFunctionArgumentsLoader : FunctionArgumentsLoader {
    val integerAssignment = listOf(RDI, RSI, RDX, RCX, R8, R9)

    override fun pushArguments(builder: ASMBuilder, arguments: List<StaticAssemblyValue>): Int {
        var integerIndex = 0
        var stackOffset = 0
        var maxStackOffset = 0
        fun buildReassignment(
                localArguments: List<StaticAssemblyValue>,
                list: MutableList<Pair<StaticAssemblyValue, StaticAssemblyValue>>
        ): List<Pair<StaticAssemblyValue, StaticAssemblyValue>> {
            for (arg in localArguments) {
                @Suppress("IMPLICIT_CAST_TO_ANY")
                when (arg) {
                    is InRegister,
                    is Int64Const,
                    is StackAddrVariable -> {
                        if (integerIndex < integerAssignment.size) {
                            list.add(arg to integerAssignment[integerIndex])
                            integerIndex++
                        } else {
                            list.add(arg to InStack(maxStackOffset - stackOffset, arg.size, true))
                            stackOffset += arg.size
                        }
                    }
                    is InStack -> {
                        // TODO check correctness of copy
                        var size = arg.size
                        var offset = 0
                        while (size > 0) {
                            val valuePart = InStack(arg.offset - offset, minOf(8, size))
                            if (integerIndex < integerAssignment.size) {
                                list.add(valuePart to integerAssignment[integerIndex])
                                integerIndex++
                            } else {
                                list.add(valuePart to InStack(maxStackOffset - stackOffset, arg.size, true))
                                stackOffset += arg.size
                            }
                            size -= valuePart.size
                            offset += valuePart.size
                        }
                    }
                    is ComplexValue -> {
                        buildReassignment(arg.list, list)
                    }
                }.apply { /* Just ensure `when` is exhaustive */ }
            }
            return list
        }
        buildReassignment(arguments, MutableEmptyList())
        maxStackOffset = stackOffset
        stackOffset = 0
        integerIndex = 0
        
        val reassignmentList = buildReassignment(arguments, arrayListOf())
        compileReassignment(builder, reassignmentList)
        if (stackOffset > 0)
            builder.appendLine("sub", RSP, stackOffset)
        return stackOffset
    }
    
    override fun pullArguments(arguments: List<Pair<Type, String>>): List<Instruction> {
        // [rbp + 16] ... [rbp + 23] -- int_arg7 (when pull)
        val list = arrayListOf<Instruction>()
        var integerIndex = 0
        var stackOffset = 16
        
        for ((type, name) in arguments) {
            val argumentVar = Variable(name)
            list.add(StackAlloc(argumentVar, type.size))
            var size = type.size
            var offset = 0
            while (size > 0) {
                val shiftedVar = Variable("$name#offset$offset")
                list.add(Add(shiftedVar, argumentVar, Int64Const(offset.toLong())))
                if (integerIndex < integerAssignment.size) {
                    list.add(Store(shiftedVar, Variable("#${integerAssignment[integerIndex].toString().toLowerCase()}")))
                    integerIndex++
                } else {
                    list.add(Store(shiftedVar, InStack(-stackOffset)))
                    stackOffset += 8
                }
                offset += 8
                size -= 8
            }
        }
        
        return list
    }
}

private class MutableEmptyList<T> : AbstractMutableList<T>() {
    override fun add(index: Int, element: T) = Unit

    override fun removeAt(index: Int): T = error("Not supported")

    override fun set(index: Int, element: T): T = error("Not supported")

    override val size: Int = 0

    override fun get(index: Int): T = error("Not supported")
}