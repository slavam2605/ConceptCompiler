package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Div
import moklev.asm.instructions.Mod
import moklev.asm.instructions.Add
import moklev.asm.instructions.Mul
import moklev.asm.instructions.Sub
import moklev.asm.utils.Type.Undefined
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class BinaryOp(ctx: ParserRuleContext, val op: String, val left: Expression, val right: Expression) : Expression(ctx) {
    override fun getType(state: CompilationState, scope: Scope): Type? {
        val leftType = left.getType(state, scope) ?: return null
        val rightType = right.getType(state, scope) ?: return null
        if (leftType == rightType)
            return leftType
        state.addError(ctx, "Different types of $op operator: $leftType and $rightType")
        return null
    }

    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val leftType = left.getType(state, scope)?.toASMType() ?: Undefined
        val rightType = right.getType(state, scope)?.toASMType() ?: Undefined
        val leftResult = left.compileResult(builder, state, scope)
        val rightResult = right.compileResult(builder, state, scope)
        val result = builder.tempVar
        builder.add(when (op) {
            "+" -> Add(leftType, Variable(result), Variable(leftResult), Variable(rightResult))
            "*" -> Mul(leftType, Variable(result), Variable(leftResult), Variable(rightResult))
            "-" -> Sub(leftType, Variable(result), Variable(leftResult), Variable(rightResult))
            "/" -> Div(leftType, Variable(result), Variable(leftResult), Variable(rightResult))
            "%" -> Mod(leftType, Variable(result), Variable(leftResult), Variable(rightResult))
            else -> TODO("not implemented")
        })
        return result
    }
}