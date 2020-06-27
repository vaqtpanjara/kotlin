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
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.defaultType

interface State {
    val fields: MutableList<Variable>
    val irClass: IrClass
    val typeArguments: MutableList<Variable>

    fun getState(symbol: IrSymbol): State? {
        return fields.firstOrNull { it.symbol == symbol }?.state
    }

    fun setField(newVar: Variable) {
        when (val oldState = fields.firstOrNull { it.symbol == newVar.symbol }) {
            null -> fields.add(newVar)                                      // newVar isn't present in value list
            else -> fields[fields.indexOf(oldState)].state = newVar.state   // newVar already present
        }
    }

    fun addTypeArguments(typeArguments: List<Variable>) {
        this.typeArguments.addAll(typeArguments)
    }

    fun getIrFunctionByIrCall(expression: IrCall): IrFunction?
}

fun State.isNull() = this is Primitive<*> && this.value == null

fun State.asInt() = (this as Primitive<*>).value as Int
fun State.asBoolean() = (this as Primitive<*>).value as Boolean
fun State.asString() = (this as Primitive<*>).value.toString()

fun State.asBooleanOrNull() = (this as? Primitive<*>)?.value as? Boolean

fun State.isSubtypeOf(other: IrType): Boolean {
    if (this is Primitive<*> && this.value == null) return other.isNullable()

    if (this is Primitive<*> && this.type.isArray() && other.isArray()) {
        val thisClass = this.typeArguments.single().state.irClass.symbol
        val otherArgument = (other as IrSimpleType).arguments.single()
        if (otherArgument is IrStarProjection) return true
        return thisClass.isSubtypeOfClass(otherArgument.typeOrNull!!.classOrNull!!)
    }

    return this.irClass.defaultType.isSubtypeOfClass(other.classOrNull!!)
}