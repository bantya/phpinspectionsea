package com.kalessil.phpStorm.phpInspectionsEA.inspectors.regularExpressions.optimizeStrategy;

import com.intellij.codeInspection.ProblemsHolder;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final public class SingleCharactersAlternationStrategy {
    private static final String messagePattern = "'%s' is 'greedy'. Please use '%s' instead.";

    final static private Pattern regexAlternations;
    static {
        /* original regex: \((\\?.)(?:\|(\\?.))+\) */
        regexAlternations = Pattern.compile("\\((\\\\?.)(?:\\|(\\\\?.))+\\)");
    }

    static public void apply(@NotNull String pattern, @NotNull StringLiteralExpression target, @NotNull ProblemsHolder holder) {
        if (!pattern.isEmpty() && pattern.indexOf('|') >= 0) {
            final Matcher regexMatcher = regexAlternations.matcher(pattern);
            if (regexMatcher.find()) {
                final List<String> branches = new ArrayList<>();
                for (int index = 1, branchesCount = regexMatcher.groupCount(); index < branchesCount; ++index) {
                    final String branch  = regexMatcher.group(index);
                    final char character = branch.charAt(branch.length() - 1);
                    branches.add((character == ']' || character == '\\' ? "\\" : "") + character);
                }
                holder.registerProblem(
                        target,
                        String.format(messagePattern, regexMatcher.group(0), String.format("([%s])", String.join("|", branches)))
                );
                branches.clear();
            }
        }
    }
}