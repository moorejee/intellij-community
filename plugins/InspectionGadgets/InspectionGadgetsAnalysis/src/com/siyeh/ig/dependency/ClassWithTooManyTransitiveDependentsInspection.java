/*
 * Copyright 2006-2022 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class ClassWithTooManyTransitiveDependentsInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public int limit = 35;

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(
    @NotNull RefEntity refEntity,
    @NotNull AnalysisScope analysisScope,
    @NotNull InspectionManager inspectionManager,
    @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefClass)) {
      return null;
    }
    final RefClass refClass = (RefClass)refEntity;
    if (!(refClass.getOwner() instanceof RefPackage)) {
      return null;
    }

    final Set<RefClass> dependencies = DependencyUtils.calculateTransitiveDependentsForClass(refClass);
    final int numDependents = dependencies.size();
    if (numDependents <= limit) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message(
      "class.with.too.many.transitive.dependents.problem.descriptor",
      refEntity.getName(), numDependents, limit);
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionGadgetsBundle.message("class.with.too.many.transitive.dependents.max.option"),
      this, "limit");
  }
}
