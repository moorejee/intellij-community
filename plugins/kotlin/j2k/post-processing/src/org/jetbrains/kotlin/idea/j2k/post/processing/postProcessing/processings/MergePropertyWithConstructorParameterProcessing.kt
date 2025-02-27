// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.postProcessing.processings

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.core.setVisibility
import org.jetbrains.kotlin.idea.intentions.addUseSiteTarget
import org.jetbrains.kotlin.idea.j2k.post.processing.postProcessing.*
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.escaped
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class MergePropertyWithConstructorParameterProcessing : ElementsBasedPostProcessing() {
    private fun KtCallableDeclaration.rename(newName: String) {
        val factory = KtPsiFactory(this)
        val escapedName = newName.escaped()
        ReferencesSearch.search(this, LocalSearchScope(containingKtFile)).forEach {
            it.element.replace(factory.createExpression(escapedName))
        }
        setName(escapedName)
    }


    private fun collectInitializations(klass: KtClass): List<Initialization<*>> {
        val parametersUsed = mutableSetOf<KtParameter>()
        val propertyUsed = mutableSetOf<KtProperty>()
        @Suppress("UNCHECKED_CAST") return klass.getAnonymousInitializers()
            .singleOrNull()
            ?.body?.safeAs<KtBlockExpression>()
            ?.statements
            ?.asSequence()
            ?.map { statement ->
                val assignment = statement.asAssignment() ?: return@map null
                val property = assignment.left
                    ?.unpackedReferenceToProperty()
                    ?.takeIf { property ->
                        property.containingClass() == klass
                                && property.initializer == null
                                && property !in propertyUsed
                    }
                    ?: return@map null
                propertyUsed += property

                when (val rightSide = assignment.right) {
                    is KtReferenceExpression -> {
                        val parameter = rightSide
                            .resolve()
                            ?.safeAs<KtParameter>()
                            ?.takeIf { parameter ->
                                parameter.containingClass() == klass
                                        && !parameter.hasValOrVar()
                                        && parameter !in parametersUsed
                            } ?: return@map null
                        val propertyType = property.type() ?: return@map null
                        val parameterType = parameter.type() ?: return@map null
                        if (!KotlinTypeChecker.DEFAULT.equalTypes(propertyType, parameterType)) return@map null
                        parametersUsed += parameter
                        ConstructorParameterInitialization(property, parameter, assignment)
                    }
                    is KtConstantExpression, is KtStringTemplateExpression -> {
                        LiteralInitialization(property, rightSide, assignment)
                    }
                    else -> null
                }
            }?.takeWhile { it != null }
            .orEmpty()
            .toList() as List<Initialization<*>>
    }

    override fun runProcessing(elements: List<PsiElement>, converterContext: NewJ2kConverterContext) {
        for (klass in runReadAction { elements.descendantsOfType<KtClass>() }) {
            convertClass(klass)
        }
    }

    private fun ConstructorParameterInitialization.mergePropertyAndConstructorParameter() {
        val (property, constructorParameter, _) = this
        val factory = KtPsiFactory(property)
        constructorParameter.addBefore(property.valOrVarKeyword, constructorParameter.nameIdentifier!!)
        constructorParameter.addAfter(factory.createWhiteSpace(), constructorParameter.valOrVarKeyword!!)
        constructorParameter.rename(property.name!!)
        val propertyCommentSaver = CommentSaver(property, saveLineBreaks = true)

        constructorParameter.setVisibility(property.visibilityModifierTypeOrDefault())
        for (annotationEntry in constructorParameter.annotationEntries) {
            if (annotationEntry.useSiteTarget == null) {
                annotationEntry.addUseSiteTarget(AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER, property.project)
            }
        }

        for (annotationEntry in property.annotationEntries) {
            constructorParameter.addAnnotationEntry(annotationEntry).also { entry ->
                if (entry.useSiteTarget == null) {
                    entry.addUseSiteTarget(AnnotationUseSiteTarget.FIELD, property.project)
                }
            }
        }

        val typeReference = property.typeReference
        if (typeReference != null) {
            for (annotationEntry in typeReference.annotationEntries) {
                constructorParameter.typeReference?.addAnnotationEntry(annotationEntry)
            }
        }

        property.delete()
        propertyCommentSaver.restore(constructorParameter, forceAdjustIndent = false)
    }

    private fun KtClass.removeEmptyInitBlocks() {
        for (initBlock in getAnonymousInitializers()) {
            if ((initBlock.body as KtBlockExpression).statements.isEmpty()) {
                val commentSaver = CommentSaver(initBlock)
                initBlock.delete()
                primaryConstructor?.let { commentSaver.restore(it) }
            }
        }
    }

    private fun KtClass.removeRedundantEnumSemicolon() {
        if (!isEnum()) return
        val enumEntries = body?.childrenOfType<KtEnumEntry>().orEmpty()
        val otherMembers = body?.childrenOfType<KtDeclaration>()?.filterNot { it is KtEnumEntry }.orEmpty()
        if (otherMembers.isNotEmpty()) return
        if (enumEntries.isNotEmpty()) {
            enumEntries.lastOrNull()?.removeSemicolon()
        } else {
            body?.removeSemicolon()
        }
    }

    private fun KtElement.removeSemicolon() {
        getChildrenOfType<LeafPsiElement>().find { it.text == ";" }?.delete()
    }

    private fun convertClass(klass: KtClass) {
        val initialisations = runReadAction { collectInitializations(klass) }
        if (initialisations.isEmpty()) return
        runUndoTransparentActionInEdt(inWriteAction = true) {
            for (initialization in initialisations) {
                val statementCommentSaver = CommentSaver(initialization.statement, saveLineBreaks = true)
                val restoreStatementCommentsTarget: KtExpression
                when (initialization) {
                    is ConstructorParameterInitialization -> {
                        initialization.mergePropertyAndConstructorParameter()
                        restoreStatementCommentsTarget = initialization.initializer
                    }
                    is LiteralInitialization -> {
                        val (property, initializer, _) = initialization
                        property.initializer = initializer
                        restoreStatementCommentsTarget = property
                    }
                }
                initialization.statement.delete()
                statementCommentSaver.restore(restoreStatementCommentsTarget, forceAdjustIndent = false)
            }
            klass.removeEmptyInitBlocks()
            klass.removeRedundantEnumSemicolon()
        }
    }
}

private sealed class Initialization<I : KtElement> {
    abstract val property: KtProperty
    abstract val initializer: I
    abstract val statement: KtBinaryExpression
}

private data class ConstructorParameterInitialization(
    override val property: KtProperty,
    override val initializer: KtParameter,
    override val statement: KtBinaryExpression
) : Initialization<KtParameter>()

private data class LiteralInitialization(
    override val property: KtProperty,
    override val initializer: KtExpression,
    override val statement: KtBinaryExpression
) : Initialization<KtExpression>()
