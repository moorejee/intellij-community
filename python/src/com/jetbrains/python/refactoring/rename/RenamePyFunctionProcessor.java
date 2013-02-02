package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class RenamePyFunctionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyFunction;
  }

  @Override
  public boolean forcesShowPreview() {
    return true;
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION;
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION;
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_FUNCTION = enabled;
  }

  @Override
  public PsiElement substituteElementToRename(PsiElement element, Editor editor) {
    PyFunction function = (PyFunction) element;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) {
      return function;
    }
    if (PyNames.INIT.equals(function.getName())) {
      return containingClass; 
    }
    final List<PsiElement> superMethods = new ArrayList<PsiElement>(PySuperMethodsSearch.search(function, true).findAll());
    if (superMethods.size() > 0) {
      final PyFunction deepestSuperMethod = PySuperMethodsSearch
        .getBaseMethod(superMethods, containingClass);
      String message = "Method " + function.getName() + " of class " + containingClass.getQualifiedName() + "\noverrides method of class "
                       + deepestSuperMethod.getContainingClass().getQualifiedName() + ".\nDo you want to rename the base method?";
      int rc = Messages.showYesNoCancelDialog(element.getProject(), message, "Rename", Messages.getQuestionIcon());
      if (rc == 0) {
        return deepestSuperMethod;
      }
      if (rc == 1) {
        return function;
      }
      return null;
    }
    final Property property = containingClass.findPropertyByCallable(function);
    if (property != null) {
      final PyTargetExpression site = property.getDefinitionSite();
      if (site != null) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          return site;
        }
        final String message = String.format("Do you want to rename the property '%s' instead of its accessor function '%s'?",
                                             property.getName(), function.getName());
        final int rc = Messages.showYesNoCancelDialog(element.getProject(), message, "Rename", Messages.getQuestionIcon());
        switch (rc) {
          case 0: return site;
          case 1: return function;
          default: return null;
        }
      }
    }
    return function;
  }

  @Override
  public void prepareRenaming(PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    PyFunction function = (PyFunction) element;
    PyOverridingMethodsSearch.search(function, true).forEach(new Processor<PyFunction>() {
      @Override
      public boolean process(PyFunction pyFunction) {
        allRenames.put(pyFunction, newName);
        return true;
      }
    });
    final PyClass containingClass = function.getContainingClass();
    if (containingClass != null) {
      final Property property = containingClass.findPropertyByCallable(function);
      if (property != null) {
        addRename(allRenames, newName, property.getGetter());
        addRename(allRenames, newName, property.getSetter());
        addRename(allRenames, newName, property.getDeleter());
      }
    }
  }

  private static void addRename(Map<PsiElement, String> renames, String newName, Maybe<Callable> accessor) {
    final Callable callable = accessor.valueOrNull();
    if (callable instanceof PyFunction) {
      renames.put(callable, newName);
    }
  }
}
