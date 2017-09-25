package moklev.asm.utils

import moklev.asm.compiler.SSATransformer
import moklev.asm.instructions.Phi
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

    fun inferTypes(instructions: Iterable<Instruction>) {
        var changed = true
        while (changed) {
            changed = false
            instructions.filterIsInstance<AssignInstruction>().forEach { instruction ->
                if (instruction is Phi) {
                    val type = instruction.pairs.asSequence().map { it.second.type }.allEquals() 
                    instruction.type = type
                }
                if (instruction.type == Type.Undefined)
                    return@forEach
                if (varTypes[instruction.lhs.name] == null)
                    changed = true
                varTypes.setUnique(instruction.lhs.name, instruction.type) { key, oldValue, newValue ->
                    "Redefining type of `$key` from `$oldValue` to `$newValue`"
                }
            }
        }
    }
    
    private fun Sequence<Type>.allEquals(): Type {
        val iterator = iterator()
        if (!iterator.hasNext())
            return Type.Undefined
        val first = iterator.next()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next == Type.Undefined)
                continue
            if (next != first) {
                error("All elements should be equal")
            }
        }
        return first
    }
    
    private fun <K, V> MutableMap<K, V>.setUnique(key: K, value: V, message: (K, V, V) -> String) {
        get(key)?.let { 
            if (it != value)
                error(message(key, it, value))
        }
        set(key, value)
    }
}