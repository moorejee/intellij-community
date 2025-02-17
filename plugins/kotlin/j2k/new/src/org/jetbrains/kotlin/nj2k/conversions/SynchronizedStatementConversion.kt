// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.tree.*


class SynchronizedStatementConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKJavaSynchronizedStatement) return recurse(element)
        element.invalidate()
        val lambdaBody = JKLambdaExpression(
            JKBlockStatement(element.body),
            emptyList()
        )
        val synchronizedCall =
            JKCallExpressionImpl(
                symbolProvider.provideMethodSymbol("kotlin.synchronized"),
                JKArgumentList(
                    element.lockExpression,
                    lambdaBody
                )
            ).withFormattingFrom(element)
        return recurse(JKExpressionStatement(synchronizedCall))
    }

}