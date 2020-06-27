/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter.state

import org.jetbrains.kotlin.backend.common.interpreter.equalTo
import org.jetbrains.kotlin.backend.common.interpreter.getFqName
import org.jetbrains.kotlin.backend.common.interpreter.stack.Variable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.nameForIrSerialization

class Lambda(val irFunction: IrFunction, override val irClass: IrClass) : State {
    override val fields: MutableList<Variable> = mutableListOf()

    // irFunction is anonymous declaration, but irCall will contain descriptor of invoke method from Function interface
    private val invokeDescriptor = irClass.declarations.single { it.nameForIrSerialization.asString() == "invoke" }.descriptor

    override fun setState(newVar: Variable) {
        throw UnsupportedOperationException("Method setState is not supported in Lambda class")
    }

    override fun getIrFunctionByIrCall(expression: IrCall): IrFunction? {
        return if (invokeDescriptor.equalTo(expression.symbol.descriptor)) irFunction else null
    }

    override fun copy(): State {
        return Lambda(irFunction, irClass).apply { this.fields.addAll(this@Lambda.fields) }
    }

    override fun toString(): String {
        val receiver = (irFunction.dispatchReceiverParameter?.type ?: irFunction.extensionReceiverParameter?.type)?.getFqName(true)
        val arguments = irFunction.valueParameters.map { it.type.getFqName(true) }.joinToString(prefix = "(", postfix = ")")
        val returnType = irFunction.returnType.getFqName(true)
        return ("$arguments -> $returnType").let { if (receiver != null) "$receiver.$it" else it }
    }
}