package moklev.asm.instructions

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * Sum of two values. Fails to compile if arguments have different types or their type differ from [type].
 * 
 * @param type required result type
 * @param lhs destination
 * @param rhs1 left operand
 * @param rhs2 right operand
 * @author Moklev Vyacheslav
 */
class Add(override var type: Type, lhs: Variable, rhs1: CompileTimeValue, rhs2: CompileTimeValue) : BinaryInstruction(lhs, rhs1, rhs2) {
    override fun toString() = "$lhs = $rhs1 + $rhs2"
    
    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Add(type, lhs, if (rhs1 == variable) value else rhs1, if (rhs2 == variable) value else rhs2)
    }

    override fun simplify(): List<Instruction> {
        if (rhs1 is Int64Const && rhs2 is Int64Const) {
            return listOf(Assign(lhs, Int64Const(rhs1.value + rhs2.value)))
        }
        if (rhs1 is Int64Const && rhs1.value == 0L) {
            return listOf(Assign(lhs, rhs2))
        }
        if (rhs2 is Int64Const && rhs2.value == 0L) {
            return listOf(Assign(lhs, rhs1))
        }
        return listOf(this)
    }

    override fun coloringPreferences(): List<ColoringPreference> {
        return listOf(
                Coalesce("$lhs", "$rhs1")
        )
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {
        val lhsValue = lhs.value(variableAssignment)!!
        val rhs1Value = rhs1.value(variableAssignment)!!
        val rhs2Value = rhs2.value(variableAssignment)!!
 
        if (rhs1.type != type || rhs2.type != type)
            error("Arguments of Add must have the same type, found: $type <${rhs1.type}, ${rhs2.type}>")
        
        compileBinaryOperation(builder, "add", lhsValue, rhs1Value, rhs2Value, symmetric = true)
    }
}