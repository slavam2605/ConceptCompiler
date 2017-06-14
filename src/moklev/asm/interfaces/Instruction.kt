package moklev.asm.interfaces

import moklev.asm.instructions.Assign
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.Variable

/**
 * @author Vyacheslav Moklev
 */
sealed class Instruction {
    abstract val usedValues: List<CompileTimeValue>
    abstract fun substitute(variable: Variable, value: CompileTimeValue): Instruction
    abstract fun simplify(): List<Instruction>
}

abstract class BinaryInstruction(final override val lhs: Variable, val rhs1: CompileTimeValue, val rhs2: CompileTimeValue) : Instruction(), AssignInstruction {
    override val usedValues = listOf(rhs1, rhs2)
}

abstract class UnaryInstruction(final override val lhs: Variable, val rhs1: CompileTimeValue) : Instruction(), AssignInstruction {
    override val usedValues = listOf(rhs1)
}

class Call(val funcName: String, val args: List<CompileTimeValue>) : Instruction() {
    override fun toString() = "call $funcName(${args.joinToString()})"
    override val usedValues = args
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        val newArgs = args.map { if (it == variable) value else it }
        return Call(funcName, newArgs)
    }
    override fun simplify() = listOf(this) 
}

abstract class BranchInstruction(val label: String) : Instruction()

class Label(val name: String) : Instruction() {
    override fun toString() = "$name:"
    override val usedValues = emptyList<CompileTimeValue>()
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this
    override fun simplify() = listOf(this)
}

class Phi(override val lhs: Variable, val pairs: List<Pair<String, CompileTimeValue>>) : Instruction(), AssignInstruction {
    override fun toString(): String {
        return "$lhs = phi ${pairs.joinToString { "[${it.first}, ${it.second}]" }}"
    }

    override val usedValues: List<CompileTimeValue>
        get() {
            val list = mutableListOf<CompileTimeValue>()
            for ((_, rhs) in pairs) {
                list.add(rhs)
            }
            return list
        }

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Phi(lhs, pairs.map { 
            if (it.second == variable) it.first to value else it
        })
    }

    override fun simplify(): List<Instruction> {
        if (pairs.isEmpty())
            return emptyList()
        if (pairs.size == 1) 
            return listOf(Assign(lhs, pairs[0].second))
        return listOf(this)
    }
}

interface AssignInstruction {
    val lhs: Variable
}
