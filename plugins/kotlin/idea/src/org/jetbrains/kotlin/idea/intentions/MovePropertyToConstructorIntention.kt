// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.LATEINIT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.VARARG_KEYWORD
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType

class MovePropertyToConstructorIntention :
  SelfTargetingIntention<KtProperty>(KtProperty::class.java, KotlinBundle.lazyMessage("move.to.constructor")),
  LocalQuickFix {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val property = descriptor.psiElement as? KtProperty ?: return
        applyTo(property, null)
    }

    override fun isApplicableTo(element: KtProperty, caretOffset: Int): Boolean {
        fun KtProperty.isDeclaredInSupportedClass(): Boolean {
            val parent = getStrictParentOfType<KtClassOrObject>()
            return parent is KtClass &&
                    !parent.isInterface() &&
                    !parent.isExpectDeclaration() &&
                    parent.secondaryConstructors.isEmpty() &&
                    parent.primaryConstructor?.hasActualModifier() != true
        }

        return !element.isLocal
                && !element.hasDelegate()
                && element.getter == null
                && element.setter == null
                && !element.hasModifier(LATEINIT_KEYWORD)
                && (element.isDeclaredInSupportedClass())
                && (element.initializer?.isValidInConstructor() ?: true)
    }

    override fun applyTo(element: KtProperty, editor: Editor?) {
        val parentClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java) ?: return
        val factory = KtPsiFactory(element)
        val primaryConstructor = parentClass.createPrimaryConstructorIfAbsent()
        val constructorParameter = element.findConstructorParameter()

        val commentSaver = CommentSaver(element)

        val context = element.analyze(BodyResolveMode.PARTIAL)
        val propertyAnnotationsText = element.modifierList?.annotationEntries?.joinToString(separator = " ") {
            it.getTextWithUseSite(context)
        }

        if (constructorParameter != null) {
            val parameterAnnotationsText =
                constructorParameter.modifierList?.annotationEntries?.joinToString(separator = " ") { it.text }

            val parameterText = buildString {
                element.modifierList?.getModifiersText()?.let(this::append)
                propertyAnnotationsText?.takeIf(String::isNotBlank)?.let { appendWithSpaceBefore(it) }
                parameterAnnotationsText?.let { appendWithSpaceBefore(it) }
                if (constructorParameter.isVarArg) appendWithSpaceBefore(VARARG_KEYWORD.value)
                appendWithSpaceBefore(element.valOrVarKeyword.text)
                element.name?.let { appendWithSpaceBefore(it) }
                constructorParameter.typeReference?.text?.let { append(": $it") }
                constructorParameter.defaultValue?.text?.let { append(" = $it") }
            }

            constructorParameter.replace(factory.createParameter(parameterText)).apply {
                commentSaver.restore(this)
            }
        } else {
            val typeText = element.typeReference?.text
                ?: (element.resolveToDescriptorIfAny() as? PropertyDescriptor)?.type?.render()
                ?: return

            val parameterText = buildString {
                element.modifierList?.getModifiersText()?.let(this::append)
                propertyAnnotationsText?.takeIf(String::isNotBlank)?.let { appendWithSpaceBefore(it) }
                appendWithSpaceBefore(element.valOrVarKeyword.text)
                element.name?.let { appendWithSpaceBefore(it) }
                appendWithSpaceBefore(": $typeText")
                element.initializer?.text?.let { append(" = $it") }
            }

            primaryConstructor.valueParameterList?.addParameter(factory.createParameter(parameterText))?.apply {
                ShortenReferences.DEFAULT.process(this)
                commentSaver.restore(this)
            }
        }

        element.delete()
    }

    private fun KtProperty.findConstructorParameter(): KtParameter? {
        val reference = initializer as? KtReferenceExpression ?: return null
        val parameterDescriptor = reference.resolveToCall()?.resultingDescriptor as? ParameterDescriptor ?: return null
        return parameterDescriptor.source.getPsi() as? KtParameter
    }

    private fun KtAnnotationEntry.getTextWithUseSite(context: BindingContext): String {
        if (useSiteTarget != null) return text
        val typeReference = this.typeReference?.text ?: return text
        val valueArgumentList = valueArgumentList?.text.orEmpty()

        fun AnnotationUseSiteTarget.textWithMe() = "@$renderName:$typeReference$valueArgumentList"

        val descriptor = context[BindingContext.ANNOTATION, this] ?: return text
        val applicableTargets = AnnotationChecker.applicableTargetSet(descriptor)
        return when {
            KotlinTarget.VALUE_PARAMETER !in applicableTargets ->
                text
            KotlinTarget.PROPERTY in applicableTargets ->
                AnnotationUseSiteTarget.PROPERTY.textWithMe()
            KotlinTarget.FIELD in applicableTargets ->
                AnnotationUseSiteTarget.FIELD.textWithMe()
            else ->
                text
        }
    }

    private fun KotlinType.render() = IdeDescriptorRenderers.SOURCE_CODE.renderType(this)

    private fun KtModifierList.getModifiersText() = getModifiers().joinToString(separator = " ") { it.text }

    private fun KtModifierList.getModifiers(): List<PsiElement> =
        node.getChildren(null).filter { it.elementType is KtModifierKeywordToken }.map { it.psi }

    private fun StringBuilder.appendWithSpaceBefore(str: String) = append(" $str")

    private fun KtExpression.isValidInConstructor(): Boolean {
        val containingClass = getStrictParentOfType<KtClass>() ?: return false
        var isValid = true
        this.accept(object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                element.acceptChildren(this)
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                val declarationDescriptor = expression.resolveToCall()?.resultingDescriptor ?: return
                if (declarationDescriptor.containingDeclaration == containingClass.descriptor) {
                    isValid = false
                }
            }
        })

        return isValid
    }
}