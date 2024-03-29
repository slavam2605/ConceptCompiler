package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Assign
import moklev.asm.utils.Int64Const
import moklev.asm.utils.Type.Int64
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.INT_64
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext


/**
 * @author Vyacheslav Moklev
 */
class IntConst(ctx: ParserRuleContext, val value: Long) : Expression(ctx) {
    override fun getType(state: CompilationState, scope: Scope): Type = INT_64

    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val result = builder.tempVar
        builder.add(Assign(Variable(result), Int64Const(value)))
        return result
    }
}