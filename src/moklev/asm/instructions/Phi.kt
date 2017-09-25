package moklev.asm.instructions

import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * Phi node of SSA graph. Controls rules of merging variable value from
 * multiple incoming blocks
 *
 * @author Moklev Vyacheslav
 */
class Phi(override var type: Type, override val lhs: Variable, val pairs: List<Pair<String, CompileTimeValue>>) : AssignInstruction {
    override fun toString(): String {
        return "$lhs = phi ${pairs.joinToString { "[${it.first}, ${it.second}]" }}"
    }

    override val usedValues: List<CompileTimeValue> = pairs.map { it.second }

    override val allValues: List<String>
        get() = mutableListOf(lhs.toString()).apply {
            pairs.forEach {
                this.add(it.first)
                this.add(it.second.toString())
            }
        }

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        return Phi(type, lhs, pairs.map {
            if (it.second == variable) it.first to value else it
        })
    }

    override fun simplify(): List<Instruction> {
        // TODO [NOT_CORRECT] not correct for short ints like int32, int16, ...
        if (pairs.isEmpty())
            return emptyList()
        if (pairs.size == 1)
            return listOf(Assign(lhs, pairs[0].second))
        if (pairs.all { it.second == pairs[0].second })
            return listOf(Assign(lhs, pairs[0].second))
        return listOf(this)
    }

    override fun coloringPreferences(): List<ColoringPreference> {
        return pairs
                .asSequence()
                .map { it.second }
                .filterIsInstance<Variable>()
                .map { Coalesce("$lhs", "$it") }
                .toList()
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) {}
}