package moklev.dummy_lang.utils

import moklev.asm.interfaces.Instruction
import moklev.asm.utils.ASMFunction

/**
 * @author Vyacheslav Moklev
 */
class FunctionBuilder(val name: String, val arguments: List<Pair<Type, String>>) {
    val instructions = ArrayList<Instruction>()
    private var labelCount = 0
    private var varCount = 0
    
    val tempLabel: String
        get() {
            val label = ".L$labelCount"
            labelCount++
            return label
        }
    
    val tempVar: String
        get() {
            val varName = "#var_$varCount"
            varCount++
            return varName
        }
    
    fun add(instruction: Instruction): FunctionBuilder {
        instructions.add(instruction)
        return this
    }
    
    fun build(): ASMFunction {
        return ASMFunction(name, arguments.map { it.first.toASMType() to it.second }, instructions)
    }
}