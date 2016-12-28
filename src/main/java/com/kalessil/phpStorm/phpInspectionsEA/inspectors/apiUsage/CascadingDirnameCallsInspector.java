package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.config.PhpLanguageLevel;
import com.jetbrains.php.config.PhpProjectConfigurationFacade;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class CascadingDirnameCallsInspector extends BasePhpInspection {

    @NotNull
    public String getShortName() {
        return "CascadingDirnameCallsInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpFunctionCall(FunctionReference reference) {
                /* general requirements */
                final String functionName = reference.getName();
                final PsiElement[] params = reference.getParameters();
                if (
                    (1 != params.length && 2 != params.length) ||
                    StringUtil.isEmpty(functionName) || !functionName.equals("dirname")
                ) {
                    return;
                }

                /* require PHP7+, where 2nd parameter has been added */
                PhpLanguageLevel php = PhpProjectConfigurationFacade.getInstance(holder.getProject()).getLanguageLevel();
                if (php.compareTo(PhpLanguageLevel.PHP700) < 0) {
                    return;
                }

                /* don't report nested calls, we want to report the one on top level */
                final PsiElement parent = reference.getParent();
                if (parent instanceof ParameterList && parent.getParent() instanceof FunctionReference) {
                    final FunctionReference parentReference = (FunctionReference) parent.getParent();
                    final String parentName         = parentReference.getName();
                    final PsiElement[] parentParams = parentReference.getParameters();
                    if (
                        (1 == parentParams.length || 2 == parentParams.length) &&
                        !StringUtil.isEmpty(parentName) && parentName.equals("dirname")
                    ) {
                        return;
                    }
                }


                int directoryLevel            = 0;
                PsiElement argument           = null;
                final List<PsiElement> levels = new ArrayList<>();

                FunctionReference current     = reference;
                while (current instanceof FunctionReference) {
                    final String currentName = current.getName();
                    if (StringUtil.isEmpty(currentName) || !currentName.equals("dirname")) {
                        break;
                    }

                    final PsiElement[] currentParams = current.getParameters();
                    if (1 == currentParams.length) {
                        argument = currentParams[0];
                        ++directoryLevel;
                    }
                    if (2 == currentParams.length) {
                        argument = currentParams[0];
                        levels.add(currentParams[1]);
                    }

                    if (!(currentParams[0] instanceof FunctionReference)) {
                        break;
                    }
                    current = (FunctionReference) currentParams[0];
                }

                /* if we have 1+ nested call (top-level one is not considered) */
                if (null != argument && (directoryLevel > 1 || levels.size() > 0)) {
                    final List<String> reported = new ArrayList<>();
                    for (PsiElement levelEntry : levels) {
                        try {
                            directoryLevel += Integer.valueOf(levelEntry.getText());
                        } catch (NumberFormatException fail) {
                            reported.add(levelEntry.getText());
                        }
                    }
                    levels.clear();

                    /* generate the replacement expression */
                    reported.add(0, String.valueOf(directoryLevel));
                    final String replacement = "dirname(%a%, %l%)"
                            .replace("%a%", argument.getText())
                            .replace("%l%", String.join(" + ", reported));
                    reported.clear();

                    final String message = "'%e%' can be used instead".replace("%e%", replacement);
                    holder.registerProblem(reference, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        };
    }
}
