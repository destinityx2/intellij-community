/*
 * Copyright 2008-2016 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThrowableResultOfMethodCallIgnoredInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "throwable.result.of.method.call.ignored.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "throwable.result.of.method.call.ignored.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowableResultOfMethodCallIgnoredVisitor();
  }

  private static class ThrowableResultOfMethodCallIgnoredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isIgnoredThrowable(expression)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiType type = method.getReturnType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      if ("propagate".equals(method.getName()) && "com.google.common.base.Throwables".equals(containingClass.getQualifiedName())) {
        return;
      }
      registerMethodCallError(expression);
    }
  }

  static boolean isIgnoredThrowable(PsiExpression expression) {
    if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_THROWABLE)) {
      return false;
    }
    return isIgnored(expression, true);
  }

  private static boolean isIgnored(PsiElement element, boolean checkDeep) {
    final PsiElement parent =
      PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiExpressionList.class, PsiVariable.class,
                                  PsiLambdaExpression.class, PsiPolyadicExpression.class, PsiInstanceOfExpression.class);
    if (parent instanceof PsiVariable) {
      if (!(parent instanceof PsiLocalVariable)) {
        return false;
      }
      else {
        return checkDeep && !isUsedElsewhere((PsiLocalVariable)parent);
      }
    }
    if (!(parent instanceof PsiStatement)) {
      return false;
    }
    if (parent instanceof PsiReturnStatement || parent instanceof PsiThrowStatement || parent instanceof PsiForeachStatement) {
      return false;
    }
    if (parent instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)parent;
      final PsiExpression expression1 = expressionStatement.getExpression();
      if (expression1 instanceof PsiMethodCallExpression) {
        // void method (like printStackTrace()) provides no result, thus can't be ignored
        return !PsiType.VOID.equals(expression1.getType());
      }
      else if (expression1 instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression1;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (!PsiTreeUtil.isAncestor(rhs, element, false)) {
          return false;
        }
        final PsiExpression lhs = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
        if (!(lhs instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return false;
        }
        return checkDeep && !isUsedElsewhere((PsiLocalVariable)target);
      }
    }
    return true;
  }

  private static boolean isUsedElsewhere(PsiLocalVariable variable) {
    final Query<PsiReference> query = ReferencesSearch.search(variable, variable.getUseScope());
    for (PsiReference reference : query) {
      final PsiElement usage = reference.getElement();
      if (!isIgnored(usage, false)) {
        return true;
      }
    }
    return false;
  }
}