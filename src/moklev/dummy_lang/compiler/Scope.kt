package moklev.dummy_lang.compiler

import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */

typealias FunctionSignature = Pair<List<Type>, Type>

class Scope {
    private val resultTypes = ArrayList<Type>()
    private val variableScope = ArrayList<MutableMap<String, Type>>()
    private val functionSignatures = HashMap<String, FunctionSignature>()
    
    init {
        variableScope.add(hashMapOf())
    }
    
    fun enterFunction(ctx: ParserRuleContext, state: CompilationState, name: String, resultType: Type, variables: List<Pair<Type, String>>) {
        resultTypes.add(resultType)
        variableScope.add(HashMap(variableScope.last()))
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
        resultTypes.removeAt(resultTypes.lastIndex)
        variableScope.removeAt(variableScope.lastIndex)
    }
    
    fun enterLocalScope() {
        variableScope.add(HashMap(variableScope.last()))
    }
    
    fun leaveLocalScope() {
        variableScope.removeAt(variableScope.lastIndex)
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
        return variableScope.last().put(name, type) != null 
    }
}