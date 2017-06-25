package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Assign
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.Statement
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Assign(ctx: ParserRuleContext, val varName: String, val expression: Expression) : Statement(ctx) {
    override fun compile(builder: FunctionBuilder) {
        val result = expression.compileResult(builder)
        builder.add(Assign(Variable(varName), Variable(result))) 
    }
}