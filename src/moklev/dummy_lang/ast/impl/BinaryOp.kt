package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Add
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class BinaryOp(ctx: ParserRuleContext, val op: String, val left: Expression, val right: Expression) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder): String {
        val leftResult = left.compileResult(builder)
        val rightResult = right.compileResult(builder)
        val result = builder.tempVar
        builder.add(when (op) {
            "+" -> Add(Variable(result), Variable(leftResult), Variable(rightResult))
            else -> TODO("not implemented")
        })
        return result
    }
}