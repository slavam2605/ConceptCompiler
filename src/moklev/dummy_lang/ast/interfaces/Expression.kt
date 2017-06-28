package moklev.dummy_lang.ast.interfaces

import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
abstract class Expression(ctx: ParserRuleContext) : Statement(ctx) {
    abstract fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String
      
    abstract fun getType(state: CompilationState, scope: Scope): Type?
    
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        compileResult(builder, state, scope)
    }
}