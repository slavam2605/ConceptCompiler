package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.CompileTimeValue
import moklev.asm.utils.InRegister
import moklev.asm.utils.StaticAssemblyValue
import moklev.asm.utils.Variable
import moklev.utils.ASMBuilder
import moklev.utils.Either

/**
 * Internal instruction that marks [lhs] as externally initialized value
 * 
 * @author Moklev Vyacheslav
 */
class ExternalAssign(lhs: Variable) : AssignInstruction(lhs) {
    override val usedValues: List<CompileTimeValue> = emptyList()

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction = this

    override fun simplify(): List<Instruction> = listOf(this)

    override fun coalescingEdges(): List<Pair<String, Either<InRegister, String>>> = emptyList()

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {}

    override fun toString(): String = "$lhs = [externally assigned]"
}