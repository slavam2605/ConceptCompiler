package moklev.dummy_lang.ast.interfaces

import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type

/**
 * @author Moklev Vyacheslav
 */
interface LValue {
    fun compileReference(builder: FunctionBuilder, state: CompilationState, scope: Scope): String
}