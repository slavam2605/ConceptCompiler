package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Coerce
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
class TypeCast(ctx: ParserRuleContext, val type: Type, val expression: Expression) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val result = expression.compileResult(builder, state, scope)
        val newResult = builder.tempVar
        builder.add(Coerce(type.toASMType(), Variable(newResult), Variable(result)))
        return newResult
    }

    override fun getType(state: CompilationState, scope: Scope): Type? {
        // TODO sizeof must be the same
        return type
    }
}