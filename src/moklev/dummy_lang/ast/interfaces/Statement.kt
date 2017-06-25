package moklev.dummy_lang.ast.interfaces

import moklev.dummy_lang.utils.FunctionBuilder
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
abstract class Statement(ctx: ParserRuleContext) : ASTNode(ctx) {
    abstract fun compile(builder: FunctionBuilder)
}