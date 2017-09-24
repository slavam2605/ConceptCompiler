package moklev.asm.compiler

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.ASMBuilder
import moklev.asm.utils.StaticAssemblyValue
import moklev.asm.utils.Type

/**
 * @author Moklev Vyacheslav
 */
interface FunctionArgumentsLoader {
    fun pushArguments(builder: ASMBuilder, arguments: List<StaticAssemblyValue>): Int

    fun pullArguments(arguments: List<Pair<Type, String>>): List<Instruction>
}