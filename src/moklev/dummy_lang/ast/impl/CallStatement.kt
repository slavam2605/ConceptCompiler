package moklev.dummy_lang.ast.impl

import moklev.asm.utils.Variable
import moklev.asm.instructions.Call
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class CallStatement(ctx: ParserRuleContext, val name: String, val arguments: List<Expression>) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder, state: CompilationState, scope: Scope) {
        // TODO type check
        val typedResults = arguments.map {
            val type = it.getType(state, scope) ?: return // TODO OOO
            val result = it.compileResult(builder, state, scope)
            type.toASMType() to Variable(result)
        }
        builder.add(Call(name, typedResults))
    }
}