package moklev.dummy_lang.ast.impl

import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class Variable(ctx: ParserRuleContext, val name: String) : Expression(ctx) {
    override fun compileResult(builder: FunctionBuilder): String {
        return name
    }
}