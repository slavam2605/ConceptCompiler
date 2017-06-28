package moklev.dummy_lang.compiler

import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
class CompilationState {
    val errorList = ArrayList<CompileError>()

    fun addError(ctx: ParserRuleContext, message: String) {
        add(ctx, message, ErrorLevel.ERROR)
    }

    fun addWarning(ctx: ParserRuleContext, message: String) {
        add(ctx, message, ErrorLevel.WARNING)
    }
    
    private fun add(ctx: ParserRuleContext, message: String, level: ErrorLevel) {
        errorList.add(CompileError(ctx.start.line, ctx.start.charPositionInLine, ctx.text, message, level))
    }
}

class CompileError(val line: Int, val pos: Int, val text: String, val message: String, val level: ErrorLevel) {
    override fun toString(): String = "$level at ($line:$pos): $message [$text]"
}

enum class ErrorLevel(val text: String) {
    ERROR("error"), WARNING("warning");

    override fun toString(): String = text
}