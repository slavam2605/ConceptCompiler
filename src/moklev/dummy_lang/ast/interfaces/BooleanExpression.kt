package moklev.dummy_lang.ast.interfaces

import moklev.asm.instructions.Assign
import moklev.asm.instructions.Jump
import moklev.asm.interfaces.Label
import moklev.asm.utils.Int64Const
import moklev.asm.utils.Type.*
import moklev.asm.utils.Variable
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Vyacheslav Moklev
 */
abstract class BooleanExpression(ctx: ParserRuleContext) : Expression(ctx) {
    abstract fun compileBranch(builder: FunctionBuilder, state: CompilationState, scope: Scope, labelIfTrue: String, labelIfFalse: String)

    override fun getType(state: CompilationState, scope: Scope): Type = Type.PrimitiveType("bool", 8) // TODO not 8

    override fun compileResult(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val setOne = builder.tempLabel
        val setZero = builder.tempLabel
        val afterSetZero = builder.tempLabel
        val result = builder.tempVar
        compileBranch(builder, state, scope, setOne, setZero)
        builder
                .add(Label(setOne))
                .add(Assign(Variable(result), Int64Const(1)))
                .add(Jump(afterSetZero))
                .add(Label(setZero))
                .add(Assign(Variable(result), Int64Const(0)))
                .add(Label(afterSetZero))
        return result
    }
}