/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.SdkConstants.FORMAT_METHOD
import com.android.tools.lint.checks.DateFormatDetector.LOCALE_CLS
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintUtils.getMethodName
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.getParentOfType
import java.util.Arrays

/**
 * Checks for errors related to locale handling
 */
/** Constructs a new [LocaleDetector]  */
class LocaleDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return if (LintClient.isStudio) {
            // In the IDE, don't flag toUpperCase/toLowerCase; these
            // are already flagged by built-in IDE inspections, so we don't
            // want duplicate warnings.
            Arrays.asList(FORMAT_METHOD, GET_DEFAULT)
        } else {
            Arrays.asList(
                // Only when not running in the IDE
                "toLowerCase", "toUpperCase", FORMAT_METHOD, GET_DEFAULT
            )
        }
    }

    override fun visitMethod(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (method.name == GET_DEFAULT) {
            if (context.evaluator.isMemberInClass(method, LOCALE_CLS)) {
                checkLocaleGetDefault(context, method, node)
            }
            return
        }

        if (context.evaluator.isMemberInClass(method, TYPE_STRING)) {
            if (method.name == FORMAT_METHOD) {
                checkFormat(context, method, node)
            } else if (method.parameterList.parametersCount == 0) {
                val location = context.getNameLocation(node)
                val message = String.format(
                    "Implicitly using the default locale is a common source of bugs: "
                            + "Use `%1\$s(Locale)` instead. For strings meant to be internal "
                            + "use `Locale.ROOT`, otherwise `Locale.getDefault()`.",
                    method.name
                )
                context.report(STRING_LOCALE, node, location, message)
            }
        }
    }

    private fun checkFormat(
        context: JavaContext,
        method: PsiMethod,
        call: UCallExpression
    ) {
        // Only check the non-locale version of String.format
        if (method.parameterList.parametersCount == 0 || !context.evaluator.parameterHasType(
                method,
                0,
                TYPE_STRING
            )) {
            return
        }

        val expressions = call.valueArguments
        if (expressions.isEmpty()) {
            return
        }

        // Find the formatting string
        val first = expressions[0]
        val value = ConstantEvaluator.evaluate(context, first) as? String ?: return

        if (StringFormatDetector.isLocaleSpecific(value)) {
            if (isLoggingParameter(context, call)) {
                return
            }

            if (call.getParentOfType<UElement>(UThrowExpression::class.java, true) != null) {
                return
            }

            val location: Location = if (FORMAT_METHOD == getMethodName(call)) {
                // For String#format, include receiver (String), but not for .toUppercase etc
                // since the receiver can often be a complex expression
                context.getCallLocation(call, true, true)
            } else {
                context.getCallLocation(call, false, true)
            }
            val message =
                "Implicitly using the default locale is a common source of bugs: " +
                        "Use `String.format(Locale, ...)` instead"
            context.report(STRING_LOCALE, call, location, message)
        }
    }

    private fun checkLocaleGetDefault(
        context: JavaContext,
        @Suppress("UNUSED_PARAMETER")
        method: PsiMethod,
        node: UCallExpression
    ) {
        val field = node.getParentOfType<UField>(UField::class.java, true) ?: return

        val evaluator = context.evaluator
        if (evaluator.isStatic(field) && evaluator.isFinal(field)) {
            context.report(
                FINAL_LOCALE,
                node,
                context.getLocation(node),
                "Assigning `Locale.getDefault()` to a final static field is suspicious; " +
                        "this code will not work correctly if the user changes locale while " +
                        "the app is running"
            )
        }
    }

    /** Returns true if the given node is a parameter to a Logging call  */
    private fun isLoggingParameter(
        context: JavaContext, node: UCallExpression
    ): Boolean {
        val parentCall =
            node.getParentOfType<UCallExpression>(UCallExpression::class.java, true)
        if (parentCall != null) {
            val name = getMethodName(parentCall)

            if (name != null && name.length == 1) { // "d", "i", "e" etc in Log
                val method = parentCall.resolve()
                return context.evaluator.isMemberInClass(method, LogDetector.LOG_CLS)
            }
        }

        return false
    }

    companion object {
        private val IMPLEMENTATION =
            Implementation(LocaleDetector::class.java, Scope.JAVA_FILE_SCOPE)

        const val GET_DEFAULT = "getDefault"

        /** Calling risky convenience methods  */
        @JvmField
        val STRING_LOCALE = Issue.create(
            "DefaultLocale",
            "Implied default locale in case conversion",
            "Calling `String#toLowerCase()` or `#toUpperCase()` **without specifying an "
                    + "explicit locale** is a common source of bugs. The reason for that is that those "
                    + "methods will use the current locale on the user's device, and even though the "
                    + "code appears to work correctly when you are developing the app, it will fail "
                    + "in some locales. For example, in the Turkish locale, the uppercase replacement "
                    + "for `i` is **not** `I`.\n"
                    + "\n"
                    + "If you want the methods to just perform ASCII replacement, for example to convert "
                    + "an enum name, call `String#toUpperCase(Locale.US)` instead. If you really want to "
                    + "use the current locale, call `String#toUpperCase(Locale.getDefault())` instead.",
            "http://developer.android.com/reference/java/util/Locale.html#default_locale",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
        )

        /** Assuming locale doesn't change  */
        @JvmField
        val FINAL_LOCALE = Issue.create(
            "ConstantLocale",
            "Constant Locale",
            "Assigning `Locale.getDefault()` to a constant is suspicious, because " +
                    "the locale can change while the app is running.",
            Category.I18N,
            6,
            Severity.WARNING,
            IMPLEMENTATION
        )
    }
}
