package moklev.dummy_lang.compiler

import moklev.asm.instructions.StackFree
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */

typealias FunctionSignature = Pair<List<Type>, Type>

class Scope {
    private val resultTypes = ArrayList<Type>()
    private val variableScope = ArrayList<MutableMap<String, Type>>()
    private val allocatedSize = ArrayList<Int>()
    private val functionSignatures = HashMap<String, FunctionSignature>()
    private val definedTypes = HashMap<String, Type>()

    init {
        variableScope.add(hashMapOf())
    }

    fun enterFunction(ctx: ParserRuleContext, state: CompilationState, name: String, resultType: Type, variables: List<Pair<Type, String>>) {
        resultTypes.add(resultType)
        variableScope.add(HashMap(variableScope.last()))
        allocatedSize.add(0)
        if (functionSignatures[name] != null) {
            state.addError(ctx, "Redefinition of function \"$name\"")
        }
        functionSignatures[name] = variables.map { it.first } to resultType
        val topScope = variableScope.last()
        for ((type, varName) in variables) {
            topScope[varName] = type
        }
    }

    fun leaveFunction() {
        resultTypes.removeLast()
        variableScope.removeLast()
        allocatedSize.removeLast()
    }

    fun enterLocalScope() {
        variableScope.add(HashMap(variableScope.last()))
        allocatedSize.add(0)
    }

    fun leaveLocalScope(builder: FunctionBuilder) {
        freeAllocatedStack(builder)
        variableScope.removeLast()
        allocatedSize.removeLast()
    }

    fun freeAllocatedStack(builder: FunctionBuilder) {
        val allocated = allocatedSize.last()
        if (allocated > 0) {
            builder.add(StackFree(allocated))
        }
    }

    fun freeAllAllocatedStacks(builder: FunctionBuilder) {
        val allocated = allocatedSize.sum()
        if (allocated > 0) {
            builder.add(StackFree(allocated))
        }
    }
    
    val resultType: Type
        get() = resultTypes.last()

    fun getType(name: String): Type? {
        return variableScope.last()[name]
    }

    fun getFunctionSignature(name: String): FunctionSignature? {
        return functionSignatures[name]
    }

    /**
     * Adds new variable to scope
     *
     * @param name name of variable
     * @param type type of variable
     * @return `true` if there were already defined variable with such name, `false` otherwise
     */
    fun add(name: String, type: Type): Boolean {
        val result = variableScope.last().put(name, type) != null
        allocatedSize[allocatedSize.lastIndex] = allocatedSize.last() + type.sizeOf
        return result
    }
 
    fun declareType(name: String, type: Type) {
        definedTypes[name] = type
    }
 
    fun getDeclaredType(name: String): Type? {
        return definedTypes[name]
    }
    
    private fun <T> MutableList<T>.removeLast() {
        removeAt(lastIndex)
    }
}