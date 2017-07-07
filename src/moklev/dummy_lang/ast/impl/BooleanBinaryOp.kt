package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.IfGreaterJump
import moklev.asm.instructions.Jump
import moklev.dummy_lang.ast.interfaces.BooleanExpression
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext
import moklev.asm.utils.Variable

/**
 * @author Moklev Vyacheslav
 */
class BooleanBinaryOp(ctx: ParserRuleContext, val op: String, val left: Expression, val right: Expression) : BooleanExpression(ctx) {
    override fun compileBranch(
            builder: FunctionBuilder,
            state: CompilationState,
            scope: Scope,
            labelIfTrue: String,
            labelIfFalse: String
    ) {
        val leftValue = left.compileResult(builder, state, scope)
        val rightValue = right.compileResult(builder, state, scope)
        builder.add(when (op) {
            ">" -> IfGreaterJump(Variable(leftValue), Variable(rightValue), labelIfTrue)
            else -> TODO("not implemented")
        })
        builder.add(Jump(labelIfFalse))
    }
}