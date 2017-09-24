package moklev.asm.instructions

import moklev.asm.compiler.IntArgumentsAssignment
import moklev.asm.compiler.LiveRange
import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.Instruction
import moklev.asm.utils.*
import moklev.asm.utils.ASMBuilder

/**
 * Call of function (subroutine)
 *
 * @author Moklev Vyacheslav
 */
class Call(val funcName: String, val args: List<Pair<Type, CompileTimeValue>>) : Instruction {
    override fun toString() = "call $funcName(${args.joinToString()})"

    override val usedValues = args.map { it.second }

    override val allValues
        get() = mutableListOf(funcName).apply {
            args.forEach { this.add(it.second.toString()) }
        }

    override fun substitute(variable: Variable, value: CompileTimeValue): Instruction {
        val newArgs = args.map { it.first to if (it.second == variable) value else it.second }
        return Call(funcName, newArgs)
    }

    override fun simplify() = listOf(this)

    override fun coloringPreferences(): List<ColoringPreference> {
        return args
                .asSequence()
                .filter { it.first == Type.Int64 }
                .take(6)
                .mapIndexedNotNull { i, pair ->
                    val variable = pair.second as? Variable ?: return@mapIndexedNotNull null
                    Target("$variable", IntArgumentsAssignment[i] as InRegister)
                }
                .toList()
    }

    override fun compile(
            builder: ASMBuilder,
            blocks: Map<String, SSATransformer.Block>,
            variableAssignment: VariableAssignment,
            currentBlockLabel: String,
            liveRange: Map<String, LiveRange>,
            indexInBlock: Int
    ) {
        // TODO [NOT_CORRECT] not correct for short ints like int32, int16, ...
        compileCall(
                builder,
                funcName,
                args,
                variableAssignment,
                currentBlockLabel,
                liveRange,
                indexInBlock
        )
    }
}