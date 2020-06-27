/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter.state

import org.jetbrains.kotlin.backend.common.interpreter.getFqName
import org.jetbrains.kotlin.backend.common.interpreter.getLastOverridden
import org.jetbrains.kotlin.backend.common.interpreter.stack.Variable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.nameForIrSerialization
import org.jetbrains.kotlin.utils.addToStdlib.cast

class Lambda(val irFunction: IrFunction, override val irClass: IrClass) : State {
    override val fields: MutableList<Variable> = mutableListOf()
    override val typeArguments: MutableList<Variable> = mutableListOf()

    private val invokeSymbol = irClass.declarations
        .single { it.nameForIrSerialization.asString() == "invoke" }
        .cast<IrSimpleFunction>()
        .getLastOverridden().symbol

    override fun getIrFunctionByIrCall(expression: IrCall): IrFunction? {
        return if (invokeSymbol == expression.symbol) irFunction else null
    }

    override fun toString(): String {
        val receiver = (irFunction.dispatchReceiverParameter?.type ?: irFunction.extensionReceiverParameter?.type)?.getFqName(true)
        val arguments = irFunction.valueParameters.map { it.type.getFqName(true) }.joinToString(prefix = "(", postfix = ")")
        val returnType = irFunction.returnType.getFqName(true)
        return ("$arguments -> $returnType").let { if (receiver != null) "$receiver.$it" else it }
    }
}