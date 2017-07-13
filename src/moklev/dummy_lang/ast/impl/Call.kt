package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.AssignCall
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class Call(ctx: ParserRuleContext, val name: String, val arguments: List<Expression>) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        // TODO type check
        val typedResults = arguments.map { 
            val type = it.getType(state, scope) ?: return "" // TODO OOO
            val result = it.compileResult(builder, state, scope)
            type.toASMType() to Variable(result)
        }
        val result = builder.tempVar
        builder.add(AssignCall(name, Variable(result), typedResults))
        return result
    }

    override fun getType(state: CompilationState, scope: Scope): Type? {
        return Type.PrimitiveType("i64") // TODO create table of defined functions
    }
}