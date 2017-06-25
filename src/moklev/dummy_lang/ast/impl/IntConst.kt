package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Assign
import moklev.asm.utils.IntConst
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class IntConst(ctx: ParserRuleContext, val value: Int) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder): String {
        val result = builder.tempVar
        builder.add(Assign(Variable(result), IntConst(value)))
        return result
    }
}