package moklev.dummy_lang.ast.interfaces

import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
abstract class Expression(ctx: ParserRuleContext) : Statement(ctx) {
    abstract fun compileResult(builder: FunctionBuilder): String

    override fun compile(builder: FunctionBuilder) {
        compileResult(builder)
    }
}