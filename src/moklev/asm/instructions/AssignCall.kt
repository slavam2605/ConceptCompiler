package moklev.asm.instructions

import moklev.asm.compiler.IntArgumentsAssignment
import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.utils.ASMBuilder
import moklev.utils.Either

/**
 * Call of function with result
 * 
 * @author Moklev Vyacheslav
 */
class AssignCall(val funcName: String, lhs: Variable, val args: List<Pair<Type, CompileTimeValue>>) : AssignInstruction(lhs) {
    override fun toString() = "$lhs = call $funcName(${args.joinToString()})"

    override val usedValues = args.map { it.second }

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        val newArgs = args.map { it.first to if (it.second == variable) value else it.second }
        return AssignCall(funcName, lhs, newArgs)
    }

    override fun simplify() = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        val result = ArrayList<ColoringPreference>()
        result.add(Target("$lhs", RAX)) // TODO depend on type
        args
                .asSequence()
                .filter { it.first == Type.INT }
                .take(6)
                .mapIndexedNotNullTo(result) { i, pair ->
                    val variable = pair.second as? Variable ?: return@mapIndexedNotNullTo null
                    Target("$variable", IntArgumentsAssignment[i] as InRegister)
                }
                .toList()
        return result
    }

    override fun compile(builder: ASMBuilder, variableAssignment: Map<String, StaticAssemblyValue>) =
            error("Not applicable")

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int
    ) {
        compileCall(
                builder,
                funcName,
                args,
                variableAssignment,
                currentBlockLabel,
                liveRange,
                indexInBlock,
                lhs.value(variableAssignment[currentBlockLabel]!!)!!,
                definingVariable = "$lhs"
        )
    }
}