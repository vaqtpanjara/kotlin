/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter.state

import org.jetbrains.kotlin.backend.common.interpreter.stack.Variable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSymbol

interface State {
    val fields: MutableList<Variable>
    val irClass: IrClass

    fun getState(symbol: IrSymbol): State? {
        return fields.firstOrNull { it.symbol == symbol }?.state
    }

    fun setField(newVar: Variable) {
        when (val oldState = fields.firstOrNull { it.symbol == newVar.symbol }) {
            null -> fields.add(newVar)                                      // newVar isn't present in value list
            else -> fields[fields.indexOf(oldState)].state = newVar.state   // newVar already present
        }
    }

    fun getIrFunctionByIrCall(expression: IrCall): IrFunction?
}

fun State.asInt() = (this as Primitive<*>).value as Int
fun State.asBoolean() = (this as Primitive<*>).value as Boolean
fun State.asString() = (this as Primitive<*>).value.toString()

fun State.asBooleanOrNull() = (this as? Primitive<*>)?.value as? Boolean