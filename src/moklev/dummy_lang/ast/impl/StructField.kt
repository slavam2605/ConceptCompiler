package moklev.dummy_lang.ast.impl

import moklev.asm.instructions.Add
import moklev.asm.utils.IntConst
import moklev.asm.utils.Variable
import moklev.dummy_lang.ast.interfaces.Expression
import moklev.dummy_lang.ast.interfaces.LValue
import moklev.dummy_lang.compiler.CompilationState
import moklev.dummy_lang.compiler.Scope
import moklev.dummy_lang.utils.FunctionBuilder
import moklev.dummy_lang.utils.Type
import org.antlr.v4.runtime.ParserRuleContext

/**
 * @author Moklev Vyacheslav
 */
class StructField(ctx: ParserRuleContext, val expression: Expression, val fieldName: String) : LValue(ctx) {
    override fun getType(state: CompilationState, scope: Scope): Type? {
        val structType = expression.getType(state, scope) ?: return null
        if (structType !is Type.StructType) {
            state.addError(ctx, "`$expression` is not a structure: expected structure type, found $structType")
            return null
        }
        val field = structType.getField(fieldName) ?: run {
            state.addError(ctx, "Type `$structType` has no field `$fieldName`")
            return null
        }
        return field.type
    }

    override fun compileReference(builder: FunctionBuilder, state: CompilationState, scope: Scope): String {
        val structType = expression.getType(state, scope) as? Type.StructType ?: return ""
        val field = structType.getField(fieldName) ?: return ""
        if (expression !is LValue) {
            state.addError(ctx, "`$expression` is not an LValue")
            return ""
        }
        val structAddress = expression.compileReference(builder, state, scope)
        // TODO introduce get_element_ptr
        val fieldAddress = builder.tempVar
        builder.add(Add(Variable(fieldAddress), Variable(structAddress), IntConst(field.offset.toLong())))
        return fieldAddress
    }
}