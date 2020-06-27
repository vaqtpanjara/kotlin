/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter.intrinsics

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.backend.common.interpreter.*
import org.jetbrains.kotlin.backend.common.interpreter.stack.Stack
import org.jetbrains.kotlin.backend.common.interpreter.stack.Variable
import org.jetbrains.kotlin.backend.common.interpreter.state.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.*

sealed class IntrinsicBase {
    abstract fun equalTo(irFunction: IrFunction): Boolean
    abstract suspend fun evaluate(irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult): ExecutionResult
}

object EmptyArray : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName in setOf("kotlin.emptyArray", "kotlin.ArrayIntrinsicsKt.emptyArray")
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        stack.pushReturnValue(emptyArray<Any?>().toState(irFunction.returnType))
        return Next
    }
}

object ArrayOf : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName == "kotlin.arrayOf"
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val array = irFunction.getArgsForMethodInvocation(stack.getAll()).toTypedArray()
        stack.pushReturnValue(array.toState(irFunction.returnType))
        return Next
    }
}

object ArrayOfNulls : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName == "kotlin.arrayOfNulls"
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val size = stack.getVariable(irFunction.valueParameters.first().symbol).state.asInt()
        val array = arrayOfNulls<Any?>(size)
        stack.pushReturnValue(array.toState(irFunction.returnType))
        return Next
    }
}

object EnumValues : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return (fqName == "kotlin.enumValues" || fqName.endsWith(".values")) && irFunction.valueParameters.isEmpty()
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val enumClass = when (irFunction.fqNameWhenAvailable.toString()) {
            "kotlin.enumValues" -> stack.getVariable(irFunction.typeParameters.first().symbol).state.irClass
            else -> irFunction.parent as IrClass
        }

        val enumEntries = enumClass.declarations.filterIsInstance<IrEnumEntry>()
            .map { entry -> entry.interpret().check { return it }.let { stack.popReturnValue() as Common } }
        stack.pushReturnValue(enumEntries.toTypedArray().toState(irFunction.returnType))
        return Next
    }
}

object EnumValueOf : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return (fqName == "kotlin.enumValueOf" || fqName.endsWith(".valueOf")) && irFunction.valueParameters.size == 1
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val enumClass = when (irFunction.fqNameWhenAvailable.toString()) {
            "kotlin.enumValueOf" -> stack.getVariable(irFunction.typeParameters.first().symbol).state.irClass
            else -> irFunction.parent as IrClass
        }
        val enumEntryName = stack.getVariable(irFunction.valueParameters.first().symbol).state.asString()
        val enumEntry = enumClass.declarations.filterIsInstance<IrEnumEntry>().singleOrNull { it.name.asString() == enumEntryName }
        enumEntry?.interpret()?.check { return it }
            ?: throw IllegalArgumentException("No enum constant ${enumClass.fqNameWhenAvailable}.$enumEntryName")

        return Next
    }
}

object RegexReplace : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName == "kotlin.text.Regex.replace" && irFunction.valueParameters.size == 2
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val states = stack.getAll().map { it.state }
        val regex = states.filterIsInstance<Wrapper>().single().value as Regex
        val input = states.filterIsInstance<Primitive<*>>().single().asString()
        val transform = states.filterIsInstance<Lambda>().single().irFunction
        val matchResultParameter = transform.valueParameters.single()
        val result = regex.replace(input) {
            val itAsState = Variable(matchResultParameter.symbol, Wrapper(it, matchResultParameter.type.classOrNull!!.owner))
            runBlocking { stack.newFrame(initPool = listOf(itAsState)) { transform.interpret() } }//.check { return it }
            stack.popReturnValue().asString()
        }
        stack.pushReturnValue(result.toState(irFunction.returnType))
        return Next
    }
}

object EnumHashCode : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName == "kotlin.Enum.hashCode"
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val hashCode = stack.getAll().single().state.hashCode()
        stack.pushReturnValue(hashCode.toState(irFunction.returnType))
        return Next
    }
}

object JsPrimitives : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName == "kotlin.Long.<init>" || fqName == "kotlin.Char.<init>"
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        when (irFunction.fqNameWhenAvailable.toString()) {
            "kotlin.Long.<init>" -> {
                val low = stack.getVariable(irFunction.valueParameters[0].symbol).state.asInt()
                val high = stack.getVariable(irFunction.valueParameters[1].symbol).state.asInt()
                stack.pushReturnValue((high.toLong().shl(32) + low).toState(irFunction.returnType))
            }
            "kotlin.Char.<init>" -> {
                val value = stack.getVariable(irFunction.valueParameters[0].symbol).state.asInt()
                stack.pushReturnValue(value.toChar().toState(irFunction.returnType))
            }
        }
        return Next
    }
}

object ArrayConstructor : IntrinsicBase() {
    override fun equalTo(irFunction: IrFunction): Boolean {
        val fqName = irFunction.fqNameWhenAvailable.toString()
        return fqName.matches("kotlin\\.(Byte|Char|Short|Int|Long|Float|Double|Boolean|)Array\\.<init>".toRegex())
    }

    override suspend fun evaluate(
        irFunction: IrFunction, stack: Stack, interpret: suspend IrElement.() -> ExecutionResult
    ): ExecutionResult {
        val sizeDescriptor = irFunction.valueParameters[0].symbol
        val size = stack.getVariable(sizeDescriptor).state.asInt()
        val arrayValue = MutableList<Any>(size) { 0 }

        if (irFunction.valueParameters.size == 2) {
            val initDescriptor = irFunction.valueParameters[1].symbol
            val initLambda = stack.getVariable(initDescriptor).state as Lambda
            val index = initLambda.irFunction.valueParameters.single()
            val nonLocalDeclarations = initLambda.extractNonLocalDeclarations()
            for (i in 0 until size) {
                val indexVar = listOf(Variable(index.symbol, i.toState(index.type)))
                // TODO throw exception if label != RETURN
                stack.newFrame(
                    asSubFrame = initLambda.irFunction.isLocal || initLambda.irFunction.isInline,
                    initPool = nonLocalDeclarations + indexVar
                ) { initLambda.irFunction.body!!.interpret() }.check(ReturnLabel.RETURN) { return it }
                arrayValue[i] = stack.popReturnValue().let { (it as? Wrapper)?.value ?: (it as? Primitive<*>)?.value ?: it }
            }
        }

        stack.pushReturnValue(arrayValue.toPrimitiveStateArray(irFunction.parentAsClass.defaultType))
        return Next
    }
}