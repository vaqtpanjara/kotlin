/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter.intrinsics

import org.jetbrains.kotlin.backend.common.interpreter.ExecutionResult
import org.jetbrains.kotlin.backend.common.interpreter.exceptions.InterpreterMethodNotFoundException
import org.jetbrains.kotlin.backend.common.interpreter.stack.Stack
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction

class IntrinsicEvaluator {
    suspend fun evaluate(irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult): ExecutionResult {
        return when {
            EmptyArray.equalTo(irFunction) -> EmptyArray.evaluate(irFunction, stack, interpret)
            ArrayOf.equalTo(irFunction) -> ArrayOf.evaluate(irFunction, stack, interpret)
            ArrayOfNulls.equalTo(irFunction) -> ArrayOfNulls.evaluate(irFunction, stack, interpret)
            EnumValues.equalTo(irFunction) -> EnumValues.evaluate(irFunction, stack, interpret)
            EnumValueOf.equalTo(irFunction) -> EnumValueOf.evaluate(irFunction, stack, interpret)
            RegexReplace.equalTo(irFunction) -> RegexReplace.evaluate(irFunction, stack, interpret)
            EnumHashCode.equalTo(irFunction) -> EnumHashCode.evaluate(irFunction, stack, interpret)
            JsPrimitives.equalTo(irFunction) -> JsPrimitives.evaluate(irFunction, stack, interpret)
            ArrayConstructor.equalTo(irFunction) -> ArrayConstructor.evaluate(irFunction, stack, interpret)
            else -> throw InterpreterMethodNotFoundException("Method ${irFunction.name} hasn't implemented")
        }
    }
}