package moklev.asm.utils

import moklev.asm.compiler.SSATransformer
import moklev.asm.interfaces.AssignInstruction
import moklev.asm.interfaces.Instruction

/**
 * @author Moklev Vyacheslav
 */
class ASMState {
    private val varTypes = HashMap<String, Type>()

    fun getVarType(name: String): Type {
        return varTypes[name] ?: Type.Undefined
    }

    fun setVarType(name: String, type: Type) {
        if (type == Type.Undefined)
            return
        val oldType = varTypes[name]
        if (oldType != null && oldType != type)
            error("Redefining type of `$name` from $oldType to $type")
        varTypes[name] = type
    }

    fun clearVarTypes() {
        varTypes.clear()
    }

    fun assignTypes(instructions: List<Instruction>) {
        for (instruction in instructions) {
            if (instruction is AssignInstruction) {
                varTypes[instruction.lhs.name] = instruction.type
            }
        }
    }
}