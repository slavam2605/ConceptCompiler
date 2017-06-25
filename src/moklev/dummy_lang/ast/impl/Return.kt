package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Return
import moklev.asm.utils.Type
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Return(ctx: ParserRuleContext, val result: Expression) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder) {
        val resultName = result.compileResult(builder)
        builder.add(Return(Type.INT, Variable(resultName))) // TODO lol get type somehow
    }
}