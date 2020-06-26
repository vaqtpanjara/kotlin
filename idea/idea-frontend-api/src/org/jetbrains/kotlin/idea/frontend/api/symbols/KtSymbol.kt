/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.symbols

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.frontend.api.Invalidatable
import org.jetbrains.kotlin.idea.frontend.api.TypeInfo
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

interface KtSymbol : Invalidatable {
    val origin: KtSymbolOrigin
    val psi: PsiElement?
}

enum class KtSymbolKind {
    TOP_LEVEL, MEMBER, LOCAL
}

interface KtNamedSymbol : KtSymbol {
    val name: Name
}

interface KtSymbolWithKind : KtSymbol {
    val symbolKind: KtSymbolKind
}

enum class KtSymbolOrigin {
    SOURCE, LIBRARY, JAVA, SAM_CONSTRUCTOR
}

abstract class KtTypeParameterSymbol : KtSymbol, KtNamedSymbol {

}

abstract class KtTypedSymbol : KtSymbol {
    abstract val type: TypeInfo
}

abstract class KtVariableLikeSymbol : KtTypedSymbol(), KtNamedSymbol, KtSymbolWithKind {
}

interface PossibleExtensionDeclaration {
    val isExtension: Boolean
    val receiverType: TypeInfo?
}

abstract class KtEnumEntrySymbol : KtVariableLikeSymbol() {
    final override val symbolKind: KtSymbolKind get() = KtSymbolKind.MEMBER
}

abstract class KtParameterSymbol : KtVariableLikeSymbol() {
}

abstract class KtVariableSymbol : KtVariableLikeSymbol() {
    abstract val isVal: Boolean
}

abstract class KtFieldSymbol : KtVariableSymbol() {
    final override val symbolKind: KtSymbolKind get() = KtSymbolKind.MEMBER
}

abstract class KtPropertySymbol : KtVariableSymbol(), PossibleExtensionDeclaration {
}

abstract class KtLocalVariableSymbol : KtVariableSymbol()

sealed class KtFunctionLikeSymbol : KtTypedSymbol(), KtSymbolWithKind {
    abstract val valueParameters: List<KtParameterSymbol>
}

abstract class KtAnonymousFunctionSymbol : KtFunctionLikeSymbol() {
    final override val symbolKind: KtSymbolKind get() = KtSymbolKind.LOCAL
}

abstract class KtFunctionSymbol : KtFunctionLikeSymbol(), KtNamedSymbol, PossibleExtensionDeclaration {
    abstract val isSuspend: Boolean
    abstract val isOperator: Boolean
    abstract val fqName: FqName?

    abstract override val valueParameters: List<KtSimpleFunctionParameterSymbol>
}

abstract class KtSimpleFunctionParameterSymbol : KtParameterSymbol()

abstract class KtConstructorSymbol : KtFunctionLikeSymbol() {
    abstract override val valueParameters: List<KtConstructorParameterSymbol>
    abstract val isPrimary: Boolean
}

abstract class KtConstructorParameterSymbol : KtParameterSymbol(), KtNamedSymbol {
    abstract val constructorParameteKind: KtConstructorParameterSymbolKind
}

enum class KtConstructorParameterSymbolKind {
    VAL_PROPERTY, VAR_PROPERTY, NON_PROPERTY
}

abstract class KtClassLikeSymbol : KtSymbol, KtNamedSymbol, KtSymbolWithKind {
    abstract val classId: ClassId
}

abstract class KtTypeAliasSymbol : KtClassLikeSymbol() {
    final override val symbolKind: KtSymbolKind get() = KtSymbolKind.TOP_LEVEL
}

abstract class KtClassOrObjectSymbol : KtClassLikeSymbol() {
    abstract val classKind: KtClassKind
}

enum class KtClassKind {
    CLASS, ABSTRACT_CLASS, ENUM_CLASS, ENUM_ENTRY, ANNOTATION_CLASS, OBJECT, COMPANION_OBJECT, INTERFACE
}