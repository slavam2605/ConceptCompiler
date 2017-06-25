package moklev.dummy_lang.ast.interfaces

import moklev.asm.instructions.Assign
import moklev.asm.instructions.Jump
import moklev.asm.interfaces.Label
import moklev.asm.utils.IntConst
import moklev.asm.utils.Variable
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
abstract class BooleanExpression(ctx: ParserRuleContext) : Expression(ctx) {
    abstract fun compileBranch(builder: FunctionBuilder, labelIfTrue: String, labelIfFalse: String)

    override fun compileResult(builder: FunctionBuilder): String {
        val setOne = builder.tempLabel
        val setZero = builder.tempLabel
        val afterSetZero = builder.tempLabel
        val result = builder.tempVar
        compileBranch(builder, setOne, setZero)
        builder
                .add(Label(setOne))
                .add(Assign(Variable(result), IntConst(1)))
                .add(Jump(afterSetZero))
                .add(Label(setZero))
                .add(Assign(Variable(result), IntConst(0)))
                .add(Label(afterSetZero))
        return result
    }
}