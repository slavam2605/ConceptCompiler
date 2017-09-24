package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.BinaryCompareJump
import moklev.asm.instructions.Jump
import moklev.asm.utils.Type
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
        val leftType = left.getType(state, scope)?.toASMType() ?: Type.Undefined
        val rightType = right.getType(state, scope)?.toASMType() ?: Type.Undefined
        
        val leftValue = left.compileResult(builder, state, scope)
        val rightValue = right.compileResult(builder, state, scope)
        builder.add(BinaryCompareJump(op, Variable(leftValue), Variable(rightValue), labelIfTrue))
        builder.add(Jump(labelIfFalse))
    }
}