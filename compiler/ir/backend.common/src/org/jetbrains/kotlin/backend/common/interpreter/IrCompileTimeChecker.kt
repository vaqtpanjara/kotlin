/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter

import org.jetbrains.kotlin.backend.common.interpreter.builtins.compileTimeAnnotation
import org.jetbrains.kotlin.backend.common.interpreter.builtins.contractsDslAnnotation
import org.jetbrains.kotlin.backend.common.interpreter.builtins.evaluateIntrinsicAnnotation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName

enum class CompileTimeMode {
    FULL, ONLY_BUILTINS
}

class IrCompileTimeChecker(
    containingDeclaration: String = "", private val mode: CompileTimeMode = CompileTimeMode.FULL
) : IrElementVisitor<Boolean, Nothing?> {
    private val callStack = mutableListOf<String>().apply { if (containingDeclaration.isNotEmpty()) add(containingDeclaration) }

    private val compileTimeTypeAliases = setOf(
        "java.lang.StringBuilder", "java.lang.IllegalArgumentException", "java.util.NoSuchElementException"
    )

    private fun IrDeclaration.isContract() = isMarkedWith(contractsDslAnnotation)
    private fun IrDeclaration.isMarkedAsEvaluateIntrinsic() = isMarkedWith(evaluateIntrinsicAnnotation)
    private fun IrDeclaration.isMarkedAsCompileTime(): Boolean = isMarkedWith(compileTimeAnnotation) ||
            (this is IrSimpleFunction && this.isFakeOverride && this.overriddenSymbols.any { it.owner.isMarkedAsCompileTime() }) ||
            (this.parent as? IrClass)?.fqNameWhenAvailable?.asString() in compileTimeTypeAliases

    private fun IrDeclaration.isMarkedWith(annotation: FqName): Boolean {
        if (this is IrClass && this.isCompanion) return false
        // must check descriptor annotations too because ir builtins operators don't have annotation on ir element
        if (this.hasAnnotation(annotation) || this.descriptor.annotations.hasAnnotation(annotation)) return true
        return (this.parent as? IrClass)?.isMarkedWith(annotation) ?: false
    }

    private fun IrProperty?.isCompileTime(): Boolean {
        if (this == null) return false
        if (this.isConst) return true
        if (this.isMarkedAsCompileTime()) return true

        val backingField = this.backingField
        val backingFieldExpression = backingField?.initializer?.expression as? IrGetValue
        return backingFieldExpression?.origin == IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER
    }

    private fun IrSymbol.withCallStack(block: () -> Boolean): Boolean {
        callStack += this.descriptor.toString()
        val result = block()
        callStack.removeAt(callStack.lastIndex)
        return result
    }

    override fun visitElement(element: IrElement, data: Nothing?) = false

    private fun visitStatements(statements: List<IrStatement>, data: Nothing?): Boolean {
        if (mode == CompileTimeMode.ONLY_BUILTINS) return false
        return statements.all { it.accept(this, data) }
    }

    private fun visitConstructor(expression: IrFunctionAccessExpression): Boolean {
        return when {
            expression.symbol.owner.isMarkedAsEvaluateIntrinsic() -> true
            !visitValueParameters(expression, null) -> false
            else -> expression.symbol.owner.isMarkedAsCompileTime()
        }
    }

    override fun visitCall(expression: IrCall, data: Nothing?): Boolean {
        if (expression.symbol.owner.isContract()) return false

        val property = (expression.symbol.owner as? IrFunctionImpl)?.correspondingPropertySymbol?.owner
        if (expression.symbol.owner.isMarkedAsCompileTime() || property.isCompileTime()) {
            val dispatchReceiverComputable = expression.dispatchReceiver?.accept(this, null) ?: true
            val extensionReceiverComputable = expression.extensionReceiver?.accept(this, null) ?: true
            if (!visitValueParameters(expression, null)) return false
            val bodyComputable = if (expression.symbol.owner.isLocal) expression.symbol.owner.body?.accept(this, null) ?: true else true

            return dispatchReceiverComputable && extensionReceiverComputable && bodyComputable
        }

        return false
    }

    override fun visitVariable(declaration: IrVariable, data: Nothing?): Boolean {
        return declaration.initializer?.accept(this, data) ?: true
    }

    private fun visitValueParameters(expression: IrFunctionAccessExpression, data: Nothing?): Boolean {
        return (0 until expression.valueArgumentsCount)
            .map { expression.getValueArgument(it) }
            .none { it?.accept(this, data) == false }
    }

    override fun visitBody(body: IrBody, data: Nothing?): Boolean {
        return visitStatements(body.statements, data)
    }

    override fun visitBlock(expression: IrBlock, data: Nothing?): Boolean {
        return visitStatements(expression.statements, data)
    }

    override fun visitSyntheticBody(body: IrSyntheticBody, data: Nothing?): Boolean {
        return body.kind == IrSyntheticBodyKind.ENUM_VALUES || body.kind == IrSyntheticBodyKind.ENUM_VALUEOF
    }

    override fun <T> visitConst(expression: IrConst<T>, data: Nothing?): Boolean = true

    override fun visitVararg(expression: IrVararg, data: Nothing?): Boolean {
        return expression.elements.any { it.accept(this, data) }
    }

    override fun visitSpreadElement(spread: IrSpreadElement, data: Nothing?): Boolean {
        return spread.expression.accept(this, data)
    }

    override fun visitComposite(expression: IrComposite, data: Nothing?): Boolean {
        if (expression.origin == IrStatementOrigin.DESTRUCTURING_DECLARATION) {
            return visitStatements(expression.statements, data)
        }
        return false
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): Boolean {
        return expression.arguments.all { it.accept(this, data) }
    }

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: Nothing?): Boolean {
        // to get object value we need nothing but it will contain only fields with compile time annotation
        return true
    }

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: Nothing?): Boolean {
        return expression.symbol.owner.initializerExpression?.accept(this, data) == true
    }

    override fun visitGetValue(expression: IrGetValue, data: Nothing?): Boolean {
        return callStack.contains(expression.symbol.descriptor.containingDeclaration.toString())
    }

    override fun visitSetVariable(expression: IrSetVariable, data: Nothing?): Boolean {
        return expression.value.accept(this, data)
    }

    override fun visitGetField(expression: IrGetField, data: Nothing?): Boolean {
        return callStack.contains(expression.symbol.descriptor.containingDeclaration.toString())
    }

    override fun visitSetField(expression: IrSetField, data: Nothing?): Boolean {
        //todo check receiver?
        return callStack.contains(expression.symbol.descriptor.containingDeclaration.toString()) && expression.value.accept(this, data)
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: Nothing?): Boolean {
        return visitConstructor(expression)
    }

    override fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Nothing?): Boolean {
        return visitConstructor(expression)
    }

    override fun visitFunctionReference(expression: IrFunctionReference, data: Nothing?): Boolean {
        return expression.symbol.withCallStack {
            expression.symbol.owner.isMarkedAsCompileTime() && expression.symbol.owner.body?.accept(this, data) == true
        }
    }

    override fun visitFunctionExpression(expression: IrFunctionExpression, data: Nothing?): Boolean {
        val isLambda = expression.origin == IrStatementOrigin.LAMBDA || expression.origin == IrStatementOrigin.ANONYMOUS_FUNCTION
        val isCompileTime = expression.function.isMarkedAsCompileTime()
        return expression.function.symbol.withCallStack {
            if (isLambda || isCompileTime) expression.function.body?.accept(this, data) == true else false
        }
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): Boolean {
        return when (expression.operator) {
            IrTypeOperator.INSTANCEOF, IrTypeOperator.NOT_INSTANCEOF,
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT,
            IrTypeOperator.CAST, IrTypeOperator.IMPLICIT_CAST, IrTypeOperator.SAFE_CAST,
            IrTypeOperator.IMPLICIT_NOTNULL -> expression.argument.accept(this, data)
            IrTypeOperator.IMPLICIT_DYNAMIC_CAST -> false
            else -> false
        }
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?): Boolean {
        return expression.branches.all { it.accept(this, data) }
    }

    override fun visitBranch(branch: IrBranch, data: Nothing?): Boolean {
        return branch.condition.accept(this, data) && branch.result.accept(this, data)
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: Nothing?): Boolean {
        return loop.condition.accept(this, data) && (loop.body?.accept(this, data) ?: true)
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Nothing?): Boolean {
        return loop.condition.accept(this, data) && (loop.body?.accept(this, data) ?: true)
    }

    override fun visitTry(aTry: IrTry, data: Nothing?): Boolean {
        if (!aTry.tryResult.accept(this, data)) return false
        if (aTry.finallyExpression != null && aTry.finallyExpression?.accept(this, data) == false) return false
        return aTry.catches.all { it.result.accept(this, data) }
    }

    override fun visitBreak(jump: IrBreak, data: Nothing?): Boolean = true

    override fun visitContinue(jump: IrContinue, data: Nothing?): Boolean = true

    override fun visitReturn(expression: IrReturn, data: Nothing?): Boolean {
        if (!callStack.contains(expression.returnTargetSymbol.descriptor.toString())) return false
        return expression.value.accept(this, data)
    }

    override fun visitThrow(expression: IrThrow, data: Nothing?): Boolean {
        return expression.value.accept(this, data)
    }
}