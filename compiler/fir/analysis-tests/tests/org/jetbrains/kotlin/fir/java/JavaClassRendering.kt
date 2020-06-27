/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.java

import org.jetbrains.kotlin.fir.FirRenderer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.java.declarations.FirJavaClass
import org.jetbrains.kotlin.fir.java.declarations.FirJavaConstructor
import org.jetbrains.kotlin.fir.java.declarations.FirJavaField
import org.jetbrains.kotlin.fir.java.declarations.FirJavaMethod
import org.jetbrains.kotlin.fir.java.scopes.JavaClassEnhancementScope
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.buildUseSiteMemberScope
import org.jetbrains.kotlin.fir.scopes.impl.FirCompositeScope

fun renderJavaClass(renderer: FirRenderer, javaClass: FirJavaClass, session: FirSession) {
    val enhancementScope = javaClass.buildUseSiteMemberScope(session, ScopeSession()).let {
        when (it) {
            is FirCompositeScope -> it.scopes.filterIsInstance<JavaClassEnhancementScope>().first()
            is JavaClassEnhancementScope -> it
            else -> null
        }
    }
    if (enhancementScope == null) {
        javaClass.accept(renderer, null)
    } else {
        renderer.visitMemberDeclaration(javaClass)
        renderer.renderSupertypes(javaClass)
        renderer.renderInBraces {
            val renderedDeclarations = mutableListOf<FirDeclaration>()
            for (declaration in javaClass.declarations) {
                if (declaration in renderedDeclarations) continue
                when (declaration) {
                    is FirJavaConstructor -> enhancementScope.processDeclaredConstructors { symbol ->
                        val enhanced = symbol.fir
                        if (enhanced !in renderedDeclarations) {
                            enhanced.accept(renderer, null)
                            renderer.newLine()
                            renderedDeclarations += enhanced
                        }
                    }
                    is FirJavaMethod -> enhancementScope.processFunctionsByName(declaration.name) { symbol ->
                        val enhanced = symbol.fir
                        if (enhanced !in renderedDeclarations) {
                            enhanced.accept(renderer, null)
                            renderer.newLine()
                            renderedDeclarations += enhanced
                        }
                    }
                    is FirJavaField -> enhancementScope.processPropertiesByName(declaration.name) { symbol ->
                        val enhanced = symbol.fir
                        if (enhanced !in renderedDeclarations) {
                            enhanced.accept(renderer, null)
                            renderer.newLine()
                            renderedDeclarations += enhanced
                        }
                    }
                    else -> {
                        declaration.accept(renderer, null)
                        renderer.newLine()
                        renderedDeclarations += declaration
                    }
                }
            }
        }
    }
}
