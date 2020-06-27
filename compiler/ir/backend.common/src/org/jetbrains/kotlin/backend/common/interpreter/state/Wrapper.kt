/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.interpreter.state

import org.jetbrains.kotlin.backend.common.interpreter.builtins.evaluateIntrinsicAnnotation
import org.jetbrains.kotlin.backend.common.interpreter.getEvaluateIntrinsicValue
import org.jetbrains.kotlin.backend.common.interpreter.getFqName
import org.jetbrains.kotlin.backend.common.interpreter.getPrimitiveClass
import org.jetbrains.kotlin.backend.common.interpreter.hasAnnotation
import org.jetbrains.kotlin.backend.common.interpreter.stack.Variable
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameForIrSerialization
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.name.FqNameUnsafe
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class Wrapper private constructor(
    val value: Any, override val irClass: IrClass, subClass: Complex?
) : Complex(irClass, mutableListOf(), null, subClass) {

    private val typeFqName = irClass.fqNameForIrSerialization.toUnsafe()
    private val receiverClass = irClass.defaultType.getClass(true)

    constructor(value: Any, irClass: IrClass) : this(value, irClass, null)

    fun getMethod(irFunction: IrFunction): MethodHandle? {
        // if function is actually a getter, then use "get${property.name.capitalize()}" as method name
        val propertyName = (irFunction as? IrFunctionImpl)?.correspondingPropertySymbol?.owner?.name?.asString()
        val propertyExplicitCall = propertyName?.takeIf { receiverClass.methods.map { it.name }.contains(it) }
        val propertyGetCall = "get${propertyName?.capitalize()}".takeIf { receiverClass.methods.map { it.name }.contains(it) }

        // intrinsicName is used to get correct java method
        // for example: - method 'get' in kotlin StringBuilder is actually 'charAt' in java StringBuilder
        val intrinsicName = irFunction.getEvaluateIntrinsicValue()
        if (intrinsicName?.isEmpty() == true) return null
        val methodName = intrinsicName ?: propertyExplicitCall ?: propertyGetCall ?: irFunction.name.toString()

        val methodType = irFunction.getMethodType()
        return MethodHandles.lookup().findVirtual(receiverClass, methodName, methodType)
    }

    override fun setState(newVar: Variable) {
        throw UnsupportedOperationException("Method setState is not supported in Wrapper class")
    }

    companion object {
        private val companionObjectValue = mapOf<String, Any>("kotlin.text.Regex\$Companion" to Regex.Companion)

        fun getCompanionObject(irClass: IrClass): Wrapper {
            val objectName = irClass.getEvaluateIntrinsicValue()!!
            val objectValue = companionObjectValue[objectName] ?: throw AssertionError("Companion object $objectName cannot be interpreted")
            return Wrapper(objectValue, irClass)
        }

        fun getConstructorMethod(irConstructor: IrFunction): MethodHandle {
            val methodType = irConstructor.getMethodType()

            return MethodHandles.lookup().findConstructor(irConstructor.returnType.getClass(true), methodType)
        }

        fun getStaticMethod(irFunction: IrFunction): MethodHandle? {
            val intrinsicName = irFunction.getEvaluateIntrinsicValue()
            if (intrinsicName?.isEmpty() == true) return null
            val jvmClassName = Class.forName(intrinsicName!!)

            val methodType = irFunction.getMethodType()
            return MethodHandles.lookup().findStatic(jvmClassName, irFunction.name.asString(), methodType)
        }

        fun getEnumEntry(enumClass: IrClass): MethodHandle? {
            val intrinsicName = enumClass.getEvaluateIntrinsicValue()
            if (intrinsicName?.isEmpty() == true) return null
            val enumClassName = Class.forName(intrinsicName!!)

            val methodType = MethodType.methodType(enumClassName, String::class.java)
            return MethodHandles.lookup().findStatic(enumClassName, "valueOf", methodType)
        }

        private fun IrFunction.getMethodType(): MethodType {
            val argsClasses = this.valueParameters.map { it.type.getClass(this.isValueParameterPrimitiveAsObject(it.index)) }
            return if (this is IrFunctionImpl) {
                // for regular methods and functions
                val returnClass = this.returnType.getClass(this.isReturnTypePrimitiveAsObject())
                val extensionClass = this.extensionReceiverParameter?.type?.getClass(this.isExtensionReceiverPrimitive())

                MethodType.methodType(returnClass, listOfNotNull(extensionClass) + argsClasses)
            } else {
                // for constructors
                MethodType.methodType(Void::class.javaPrimitiveType, argsClasses)
            }
        }

        private fun IrType.getClass(asObject: Boolean): Class<out Any> {
            val fqName = this.getFqName()
            val owner = this.classOrNull?.owner
            return when {
                this.isPrimitiveType() -> getPrimitiveClass(fqName!!, asObject)
                this.isArray() -> if (asObject) Array<Any?>::class.javaObjectType else Array<Any?>::class.java
                owner.hasAnnotation(evaluateIntrinsicAnnotation) -> Class.forName(owner!!.getEvaluateIntrinsicValue())
                //TODO primitive array
                this.isTypeParameter() -> Any::class.java
                else -> JavaToKotlinClassMap.mapKotlinToJava(FqNameUnsafe(fqName!!))?.let { Class.forName(it.asSingleFqName().toString()) }
            } ?: Class.forName(fqName)
        }

        private fun IrFunction.getOriginalOverriddenSymbols(): MutableList<IrFunctionSymbol> {
            val overriddenSymbols = mutableListOf<IrFunctionSymbol>()
            if (this is IrFunctionImpl) {
                val pool = this.overriddenSymbols.toMutableList()
                val iterator = pool.listIterator()
                for (symbol in iterator) {
                    if (symbol.owner.overriddenSymbols.isEmpty()) {
                        overriddenSymbols += symbol
                        iterator.remove()
                    } else {
                        symbol.owner.overriddenSymbols.forEach { iterator.add(it) }
                    }
                }
            }

            if (overriddenSymbols.isEmpty()) overriddenSymbols.add(this.symbol)
            return overriddenSymbols
        }

        private fun IrFunction.isExtensionReceiverPrimitive(): Boolean {
            return this.extensionReceiverParameter?.type?.isPrimitiveType() == false
        }

        private fun IrFunction.isReturnTypePrimitiveAsObject(): Boolean {
            for (symbol in getOriginalOverriddenSymbols()) {
                if (!symbol.owner.returnType.isTypeParameter() && !symbol.owner.returnType.isNullable()) {
                    return false
                }
            }
            return true
        }

        private fun IrFunction.isValueParameterPrimitiveAsObject(index: Int): Boolean {
            for (symbol in getOriginalOverriddenSymbols()) {
                if (!symbol.owner.valueParameters[index].type.isTypeParameter() && !symbol.owner.valueParameters[index].type.isNullable()) {
                    return false
                }
            }
            return true
        }
    }

    override fun copy(): State {
        return Wrapper(value, irClass, subClass ?: this)
    }

    override fun toString(): String {
        return "Wrapper(obj='$typeFqName', value=$value)"
    }
}