/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter

import org.jetbrains.kotlin.backend.common.interpreter.builtins.evaluateIntrinsicAnnotation
import org.jetbrains.kotlin.backend.common.interpreter.stack.Variable
import org.jetbrains.kotlin.backend.common.interpreter.state.*
import org.jetbrains.kotlin.builtins.UnsignedTypes
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun IrFunction.getDispatchReceiver(): IrValueParameterSymbol? {
    return this.dispatchReceiverParameter?.symbol
}

fun IrFunction.getExtensionReceiver(): IrValueParameterSymbol? {
    return this.extensionReceiverParameter?.symbol
}

fun IrFunction.getReceiver(): IrSymbol? {
    return this.getDispatchReceiver() ?: this.getExtensionReceiver()
}

fun IrFunctionAccessExpression.getBody(): IrBody? {
    return this.symbol.owner.body
}

fun State.toIrExpression(expression: IrExpression): IrExpression {
    val start = expression.startOffset
    val end = expression.endOffset
    return when (this) {
        is Primitive<*> ->
            when (this.value) {
                // toIrConst call is necessary to replace ir offsets
                is Boolean, is Char, is Byte, is Short, is Int, is Long, is String, is Float, is Double ->
                    this.value.toIrConst(this.type, start, end)
                null -> this.value.toIrConst(this.type, start, end)
                else -> expression // TODO support for arrays
            }
        is Complex -> {
            val type = this.irClass.defaultType.toKotlinType()
            when {
                UnsignedTypes.isUnsignedType(type) ->
                    (this.fields.single().state as Primitive<*>).value.toIrConst(this.irClass.defaultType, start, end)
                else -> expression
            }
        }
        else -> expression // TODO support
    }
}

fun Any?.toState(irType: IrType): State {
    return when (this) {
        is State -> this
        is Boolean, is Char, is Byte, is Short, is Int, is Long, is String, is Float, is Double, is Array<*>, is ByteArray,
        is CharArray, is ShortArray, is IntArray, is LongArray, is FloatArray, is DoubleArray, is BooleanArray -> Primitive(this, irType)
        null -> Primitive(this, irType)
        else -> Wrapper(this, irType.classOrNull!!.owner)
    }
}

fun Any?.toIrConst(irType: IrType, startOffset: Int = UNDEFINED_OFFSET, endOffset: Int = UNDEFINED_OFFSET): IrConst<*> {
    return when (this) {
        is Boolean -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Boolean, this)
        is Char -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Char, this)
        is Byte -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Byte, this)
        is Short -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Short, this)
        is Int -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Int, this)
        is Long -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Long, this)
        is String -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.String, this)
        is Float -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Float, this)
        is Double -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Double, this)
        null -> IrConstImpl(startOffset, endOffset, irType, IrConstKind.Null, this)
        else -> throw UnsupportedOperationException("Unsupported const element type ${this::class}")
    }
}

fun <T> IrConst<T>.toPrimitive(): Primitive<T> {
    return Primitive(this.value, this.type)
}

fun IrAnnotationContainer?.hasAnnotation(annotation: FqName): Boolean {
    this ?: return false
    if (this.annotations.isNotEmpty()) {
        return this.annotations.any { it.symbol.owner.parentAsClass.fqNameWhenAvailable == annotation }
    }
    return false
}

fun IrAnnotationContainer.getAnnotation(annotation: FqName): IrConstructorCall {
    return this.annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == annotation }
        ?: ((this as IrFunction).parent as IrClass).annotations.first { it.symbol.owner.parentAsClass.fqNameWhenAvailable == annotation }
}

fun IrAnnotationContainer.getEvaluateIntrinsicValue(): String? {
    if (this is IrClass && this.fqNameWhenAvailable?.startsWith(Name.identifier("java")) == true) return this.fqNameWhenAvailable?.asString()
    if (!this.hasAnnotation(evaluateIntrinsicAnnotation)) return null
    return (this.getAnnotation(evaluateIntrinsicAnnotation).getValueArgument(0) as IrConst<*>).value.toString()
}

fun getPrimitiveClass(fqName: String, asObject: Boolean = false): Class<*>? {
    return when (fqName) {
        "kotlin.Boolean" -> if (asObject) Boolean::class.javaObjectType else Boolean::class.java
        "kotlin.Char" -> if (asObject) Char::class.javaObjectType else Char::class.java
        "kotlin.Byte" -> if (asObject) Byte::class.javaObjectType else Byte::class.java
        "kotlin.Short" -> if (asObject) Short::class.javaObjectType else Short::class.java
        "kotlin.Int" -> if (asObject) Int::class.javaObjectType else Int::class.java
        "kotlin.Long" -> if (asObject) Long::class.javaObjectType else Long::class.java
        "kotlin.String" -> if (asObject) String::class.javaObjectType else String::class.java
        "kotlin.Float" -> if (asObject) Float::class.javaObjectType else Float::class.java
        "kotlin.Double" -> if (asObject) Double::class.javaObjectType else Double::class.java
        else -> null
    }
}

fun IrType.getFqName(withNullableSymbol: Boolean = false): String? {
    return this.classOrNull?.owner?.fqNameWhenAvailable?.asString()?.let { if (this.isNullable() && withNullableSymbol) "$it?" else it }
}

fun IrFunction.getArgsForMethodInvocation(args: List<Variable>): List<Any?> {
    val argsValues = args.map {
        when (val state = it.state) {
            is ExceptionState -> state.getThisAsCauseForException()
            is Wrapper -> state.value
            is Primitive<*> -> state.value
            else -> throw AssertionError("${state::class} is unsupported as argument for wrapper method invocation")
        }
    }.toMutableList()

    // TODO if vararg isn't last parameter
    // must convert vararg array into separated elements for correct invoke
    if (this.valueParameters.lastOrNull()?.varargElementType != null) {
        val varargValue = argsValues.last()
        argsValues.removeAt(argsValues.size - 1)
        argsValues.addAll(varargValue as Array<out Any?>)
    }

    return argsValues
}

fun IrFunction.getLastOverridden(): IrFunction {
    if (this !is IrSimpleFunction) return this

    return generateSequence(listOf(this)) { it.firstOrNull()?.overriddenSymbols?.map { it.owner } }.flatten().last()
}

fun List<Any?>.toPrimitiveStateArray(type: IrType): Primitive<*> {
    return when (type.getFqName()) {
        "kotlin.ByteArray" -> Primitive(ByteArray(size) { i -> (this[i] as Number).toByte() }, type)
        "kotlin.CharArray" -> Primitive(CharArray(size) { i -> this[i] as Char }, type)
        "kotlin.ShortArray" -> Primitive(ShortArray(size) { i -> (this[i] as Number).toShort() }, type)
        "kotlin.IntArray" -> Primitive(IntArray(size) { i -> (this[i] as Number).toInt() }, type)
        "kotlin.LongArray" -> Primitive(LongArray(size) { i -> (this[i] as Number).toLong() }, type)
        "kotlin.FloatArray" -> Primitive(FloatArray(size) { i -> (this[i] as Number).toFloat() }, type)
        "kotlin.DoubleArray" -> Primitive(DoubleArray(size) { i -> (this[i] as Number).toDouble() }, type)
        "kotlin.BooleanArray" -> Primitive(BooleanArray(size) { i -> this[i].toString().toBoolean() }, type)
        else -> Primitive<Array<*>>(this.toTypedArray(), type)
    }
}

fun IrFunctionAccessExpression.getVarargType(index: Int): IrType? {
    val varargType = this.symbol.owner.valueParameters[index].varargElementType ?: return null
    varargType.classOrNull?.let { return this.symbol.owner.valueParameters[index].type }
    val typeParameter = varargType.classifierOrFail.owner as IrTypeParameter
    return this.getTypeArgument(typeParameter.index)
}

fun getTypeArguments(
    container: IrTypeParametersContainer, expression: IrFunctionAccessExpression, mapper: (IrTypeParameterSymbol) -> State
): List<Variable> {
    fun IrType.getState(): State {
        return this.classOrNull?.owner?.let { Common(it) } ?: mapper(this.classifierOrFail as IrTypeParameterSymbol)
    }

    val typeArguments = container.typeParameters.mapIndexed { index, typeParameter ->
        val typeArgument = expression.getTypeArgument(index)!!
        Variable(typeParameter.symbol, typeArgument.getState())
    }.toMutableList()

    if (container is IrSimpleFunction) {
        container.returnType.classifierOrFail.owner.safeAs<IrTypeParameter>()
            ?.let { typeArguments.add(Variable(it.symbol, expression.type.getState())) }
    }

    return typeArguments
}

fun State?.extractNonLocalDeclarations(): List<Variable> {
    this ?: return listOf()
    val state = this.takeIf { it !is Complex } ?: (this as Complex).getOriginal()
    return state.fields.filter { it.symbol !is IrFieldSymbol }
}

fun State?.getCorrectReceiverByFunction(irFunction: IrFunction): State? {
    if (this !is Complex) return this

    val original: Complex? = this.getOriginal()
    val other = irFunction.parentClassOrNull?.thisReceiver ?: return this
    return generateSequence(original) { it.superClass }.firstOrNull { it.irClass.thisReceiver == other } ?: this
}

fun State.checkNullability(irType: IrType?): State {
    if (irType !is IrSimpleType) return this
    if (this.isNull() && !irType.hasQuestionMark) {
        throw NullPointerException()
    }
    return this
}

fun IrValueParameterSymbol.isNullable(): Boolean {
    val type = this.owner.type as? IrSimpleType ?: return false
    return type.isNullable()
}

fun IrFunction.getCapitalizedFileName() = this.file.name.replace(".kt", "Kt").capitalize()