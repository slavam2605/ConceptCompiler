package moklev.dummy_lang.compiler

import moklev.dummy_lang.utils.Type

/**
 * @author Vyacheslav Moklev
 */
class Scope(resultType: Type, variables: List<Pair<Type, String>>) {
    private val resultTypes = ArrayList<Type>()
    private val variableScope = ArrayList<MutableMap<String, Type>>()
    
    init {
        resultTypes.add(resultType)
        variableScope.add(HashMap())
        for ((type, name) in variables) {
            variableScope[0][name] = type
        }
    }
    
    val resultType: Type = resultTypes.last()
    
    fun getType(name: String): Type? {
        return variableScope.last()[name]
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