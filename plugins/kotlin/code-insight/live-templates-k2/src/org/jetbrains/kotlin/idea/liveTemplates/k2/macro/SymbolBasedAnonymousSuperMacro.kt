// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.liveTemplates.k2.macro

import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.idea.liveTemplates.macro.AbstractAnonymousSuperMacro
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile

internal class SymbolBasedAnonymousSuperMacro : AbstractAnonymousSuperMacro() {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun resolveSupertypes(expression: KtExpression, file: KtFile): Collection<PsiNamedElement> {
        allowAnalysisOnEdt {
            analyze(expression) {
                val scope = file.getScopeContextForPosition(expression).scopes
                return scope.getClassifierSymbols()
                    .filterIsInstance<KtNamedClassOrObjectSymbol>()
                    .filter {
                        when (it.classKind) {
                            KtClassKind.CLASS, KtClassKind.INTERFACE -> true
                            KtClassKind.ANNOTATION_CLASS -> it.origin != KtSymbolOrigin.JAVA
                            else -> false
                        }
                    }
                    .mapNotNull { it.psi as? PsiNamedElement }
                    .toList()
            }
        }
    }
}