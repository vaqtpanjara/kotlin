/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.impl.FirValueParameterImpl
import org.jetbrains.kotlin.idea.frontend.api.Invalidatable
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.*
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.FirKtClassOrObjectSymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.FirKtLocalVariableSymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.FirKtPropertySymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.FirKtFunctionSymbol
import org.jetbrains.kotlin.idea.frontend.api.fir.symbols.FirKtSimpleFunctionValueParameterSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtVariableSymbol

internal class KtSymbolByFirBuilder(private val validityToken: Invalidatable) {
    fun buildSymbol(fir: FirDeclaration): KtSymbol = when (fir) {
        is FirRegularClass -> buildClassSymbol(fir)
        is FirSimpleFunction -> buildFunctionSymbol(fir)
        is FirProperty -> buildPropertySymbol(fir)
        is FirValueParameterImpl -> buildParameterSymbol(fir)
        is FirConstructor -> buildFirConstructorSymbol(fir)
        is FirTypeParameter -> buildFirTypeParameterSymbol(fir)
        is FirTypeAlias -> buildFirTypeAliasSymbol(fir)
        is FirEnumEntry -> buildFirEnumEntrySymbol(fir)
        is FirField -> buildFirFieldSymbol(fir)
        is FirAnonymousFunction -> buildFirAnonymousFunction(fir)
        else ->
            TODO(fir::class.toString())
    }

    fun buildClassSymbol(fir: FirRegularClass) = FirKtClassOrObjectSymbol(fir, validityToken)

    // TODO it can be a constructor parameter, which may be split into parameter & property
    // we should handle them both
    fun buildParameterSymbol(fir: FirValueParameterImpl) = FirKtSimpleFunctionValueParameterSymbol(fir, validityToken)

    fun buildFunctionSymbol(fir: FirSimpleFunction) = FirKtFunctionSymbol(fir, validityToken, this)
    fun buildFirConstructorSymbol(fir: FirConstructor) = FirKtConstructorSymbol(fir, validityToken)
    fun buildFirTypeParameterSymbol(fir: FirTypeParameter) = FirKtTypeParameterSymbol(fir, validityToken)
    fun buildFirTypeAliasSymbol(fir: FirTypeAlias) = FirKtTypeAliasSymbol(fir, validityToken)
    fun buildFirEnumEntrySymbol(fir: FirEnumEntry) = FirKtEnumEntrySymbol(fir, validityToken)
    fun buildFirFieldSymbol(fir: FirField) = FirKtFieldSymbol(fir, validityToken)
    fun buildFirAnonymousFunction(fir: FirAnonymousFunction) = FirKtAnonymousFunctionSymbol(fir, validityToken, this)

    fun buildPropertySymbol(fir: FirProperty): KtVariableSymbol {
        return when {
            fir.isLocal -> FirKtLocalVariableSymbol(fir, validityToken)
            else -> FirKtPropertySymbol(fir, validityToken)
        }
    }
}

internal fun FirElement.buildSymbol(builder: KtSymbolByFirBuilder) =
    (this as? FirDeclaration)?.let(builder::buildSymbol)

internal fun FirDeclaration.buildSymbol(builder: KtSymbolByFirBuilder) =
    builder.buildSymbol(this)