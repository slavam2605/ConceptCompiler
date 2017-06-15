package moklev.asm.utils

import moklev.asm.interfaces.Instruction

/**
 * @author Moklev Vyacheslav
 */
class ASMFunction(val name: String, val arguments: List<Pair<Type, String>>, val instructions: List<Instruction>)