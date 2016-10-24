/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_FRAGMENT;
import static com.android.SdkConstants.CLASS_RESOURCES;
import static com.android.SdkConstants.CLASS_V4_FRAGMENT;
import static com.android.SdkConstants.CLS_TYPED_ARRAY;
import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.EnumSet;
import java.util.Locale;

/** Evaluates constant expressions */
public class ResourceEvaluator {

    /**
     * Marker ResourceType used to signify that an expression is of type {@code @ColorInt},
     * which isn't actually a ResourceType but one we want to specifically compare with.
     * We're using {@link ResourceType#PUBLIC} because that one won't appear in the R
     * class (and ResourceType is an enum we can't just create new constants for.)
     */
    public static final ResourceType COLOR_INT_MARKER_TYPE = ResourceType.PUBLIC;
    /**
     * Marker ResourceType used to signify that an expression is of type {@code @Px},
     * which isn't actually a ResourceType but one we want to specifically compare with.
     * We're using {@link ResourceType#DECLARE_STYLEABLE} because that one doesn't
     * have a corresponding {@code *Res} constant (and ResourceType is an enum we can't
     * just create new constants for.)
     */
    public static final ResourceType PX_MARKER_TYPE = ResourceType.DECLARE_STYLEABLE;

    public static final String COLOR_INT_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "ColorInt";
    public static final String PX_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "Px";
    public static final String RES_SUFFIX = "Res";

    private final JavaEvaluator evaluator;

    private boolean allowDereference = true;

    /**
     * Creates a new resource evaluator
     *
     * @param evaluator the evaluator to use to resolve annotations references, if any
     */
    public ResourceEvaluator(@Nullable JavaEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Whether we allow dereferencing resources when computing constants;
     * e.g. if we ask for the resource for {@code x} when the code is
     * {@code x = getString(R.string.name)}, if {@code allowDereference} is
     * true we'll return R.string.name, otherwise we'll return null.
     *
     * @return this for constructor chaining
     */
    public ResourceEvaluator allowDereference(boolean allow) {
        allowDereference = allow;
        return this;
    }

    /**
     * Evaluates the given node and returns the resource reference (type and name) it
     * points to, if any
     *
     * @param evaluator the evaluator to use to look up annotations
     * @param element the node to compute the constant value for
     * @return the corresponding resource url (type and name)
     */
    @Nullable
    public static ResourceUrl getResource(
            @Nullable JavaEvaluator evaluator,
            @NonNull PsiElement element) {
        return new ResourceEvaluator(evaluator).getResource(element);
    }

    /**
     * Evaluates the given node and returns the resource types implied by the given element,
     * if any.
     *
     * @param evaluator the evaluator to use to look up annotations
     * @param element the node to compute the constant value for
     * @return the corresponding resource types
     */
    @Nullable
    public static EnumSet<ResourceType> getResourceTypes(
            @Nullable JavaEvaluator evaluator,
            @NonNull PsiElement element) {
        return new ResourceEvaluator(evaluator).getResourceTypes(element);
    }

    /**
     * Evaluates the given node and returns the resource reference (type and name) it
     * points to, if any
     *
     * @param element the node to compute the constant value for
     * @return the corresponding constant value - a String, an Integer, a Float, and so on
     */
    @Nullable
    public ResourceUrl getResource(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) element;
            Object known = ConstantEvaluator.evaluate(null, expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return getResource(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return getResource(expression.getElseExpression());
            }
        } else if (element instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) element;
            return getResource(parenthesizedExpression.getExpression());
        } else if (element instanceof PsiMethodCallExpression && allowDereference) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) element;
            PsiReferenceExpression expression = call.getMethodExpression();
            PsiMethod method = call.resolveMethod();
            if (method != null && method.getContainingClass() != null) {
                String qualifiedName = method.getContainingClass().getQualifiedName();
                String name = expression.getReferenceName();
                if ((CLASS_RESOURCES.equals(qualifiedName)
                        || CLASS_CONTEXT.equals(qualifiedName)
                        || CLASS_FRAGMENT.equals(qualifiedName)
                        || CLASS_V4_FRAGMENT.equals(qualifiedName)
                        || CLS_TYPED_ARRAY.equals(qualifiedName))
                        && name != null
                        && name.startsWith("get")) {
                    PsiExpression[] args = call.getArgumentList().getExpressions();
                    if (args.length > 0) {
                        return getResource(args[0]);
                    }
                }
            }
        } else if (element instanceof PsiReference) {
            ResourceUrl url = getResourceConstant(element);
            if (url != null) {
                return url;
            }
            PsiElement resolved = ((PsiReference) element).resolve();
            if (resolved instanceof PsiField) {
                url = getResourceConstant(resolved);
                if (url != null) {
                    return url;
                }
                PsiField field = (PsiField) resolved;
                if (field.getInitializer() != null) {
                    return getResource(field.getInitializer());
                }
                return null;
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class,
                        false);
                if (statement != null) {
                    PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement,
                            PsiStatement.class);
                    String targetName = variable.getName();
                    if (targetName == null) {
                        return null;
                    }
                    while (prev != null) {
                        if (prev instanceof PsiDeclarationStatement) {
                            PsiDeclarationStatement prevStatement = (PsiDeclarationStatement) prev;
                            for (PsiElement e : prevStatement.getDeclaredElements()) {
                                if (variable.equals(e)) {
                                    return getResource(variable.getInitializer());
                                }
                            }
                        } else if (prev instanceof PsiExpressionStatement) {
                            PsiExpression expression = ((PsiExpressionStatement) prev)
                                    .getExpression();
                            if (expression instanceof PsiAssignmentExpression) {
                                PsiAssignmentExpression assign
                                        = (PsiAssignmentExpression) expression;
                                PsiExpression lhs = assign.getLExpression();
                                if (lhs instanceof PsiReferenceExpression) {
                                    PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
                                    if (targetName.equals(reference.getReferenceName()) &&
                                            reference.getQualifier() == null) {
                                        return getResource(assign.getRExpression());
                                    }
                                }
                            }
                        }
                        prev = PsiTreeUtil.getPrevSiblingOfType(prev,
                                PsiStatement.class);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Evaluates the given node and returns the resource types applicable to the
     * node, if any.
     *
     * @param element the element to compute the types for
     * @return the corresponding resource types
     */
    @Nullable
    public EnumSet<ResourceType> getResourceTypes(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof PsiConditionalExpression) {
            PsiConditionalExpression expression = (PsiConditionalExpression) element;
            Object known = ConstantEvaluator.evaluate(null, expression.getCondition());
            if (known == Boolean.TRUE && expression.getThenExpression() != null) {
                return getResourceTypes(expression.getThenExpression());
            } else if (known == Boolean.FALSE && expression.getElseExpression() != null) {
                return getResourceTypes(expression.getElseExpression());
            } else {
                EnumSet<ResourceType> left = getResourceTypes(
                        expression.getThenExpression());
                EnumSet<ResourceType> right = getResourceTypes(
                        expression.getElseExpression());
                if (left == null) {
                    return right;
                } else if (right == null) {
                    return left;
                } else {
                    EnumSet<ResourceType> copy = EnumSet.copyOf(left);
                    copy.addAll(right);
                    return copy;
                }
            }
        } else if (element instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) element;
            return getResourceTypes(parenthesizedExpression.getExpression());
        } else if (element instanceof PsiMethodCallExpression && allowDereference) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) element;
            PsiReferenceExpression expression = call.getMethodExpression();
            PsiMethod method = call.resolveMethod();
            if (method != null && method.getContainingClass() != null) {
                EnumSet<ResourceType> types = getTypesFromAnnotations(method);
                if (types != null) {
                    return types;
                }

                String qualifiedName = method.getContainingClass().getQualifiedName();
                String name = expression.getReferenceName();
                if ((CLASS_RESOURCES.equals(qualifiedName)
                        || CLASS_CONTEXT.equals(qualifiedName)
                        || CLASS_FRAGMENT.equals(qualifiedName)
                        || CLASS_V4_FRAGMENT.equals(qualifiedName)
                        || CLS_TYPED_ARRAY.equals(qualifiedName))
                        && name != null
                        && name.startsWith("get")) {
                    PsiExpression[] args = call.getArgumentList().getExpressions();
                    if (args.length > 0) {
                        types = getResourceTypes(args[0]);
                        if (types != null) {
                            return types;
                        }
                    }
                }
            }
        } else if (element instanceof PsiReference) {
            ResourceUrl url = getResourceConstant(element);
            if (url != null) {
                return EnumSet.of(url.type);
            }
            PsiElement resolved = ((PsiReference) element).resolve();
            if (resolved instanceof PsiField) {
                url = getResourceConstant(resolved);
                if (url != null) {
                    return EnumSet.of(url.type);
                }
                PsiField field = (PsiField) resolved;
                if (field.getInitializer() != null) {
                    return getResourceTypes(field.getInitializer());
                }
                return null;
            } else if (resolved instanceof PsiParameter) {
                return getTypesFromAnnotations((PsiParameter)resolved);
            } else if (resolved instanceof PsiLocalVariable) {
                PsiLocalVariable variable = (PsiLocalVariable) resolved;
                PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class,
                        false);
                if (statement != null) {
                    PsiStatement prev = PsiTreeUtil.getPrevSiblingOfType(statement,
                            PsiStatement.class);
                    String targetName = variable.getName();
                    if (targetName == null) {
                        return null;
                    }
                    while (prev != null) {
                        if (prev instanceof PsiDeclarationStatement) {
                            PsiDeclarationStatement prevStatement = (PsiDeclarationStatement) prev;
                            for (PsiElement e : prevStatement.getDeclaredElements()) {
                                if (variable.equals(e)) {
                                    return getResourceTypes(variable.getInitializer());
                                }
                            }
                        } else if (prev instanceof PsiExpressionStatement) {
                            PsiExpression expression = ((PsiExpressionStatement) prev)
                                    .getExpression();
                            if (expression instanceof PsiAssignmentExpression) {
                                PsiAssignmentExpression assign
                                        = (PsiAssignmentExpression) expression;
                                PsiExpression lhs = assign.getLExpression();
                                if (lhs instanceof PsiReferenceExpression) {
                                    PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
                                    if (targetName.equals(reference.getReferenceName()) &&
                                            reference.getQualifier() == null) {
                                        return getResourceTypes(assign.getRExpression());
                                    }
                                }
                            }
                        }
                        prev = PsiTreeUtil.getPrevSiblingOfType(prev,
                                PsiStatement.class);
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private EnumSet<ResourceType> getTypesFromAnnotations(PsiModifierListOwner owner) {
        if (evaluator == null) {
            return null;
        }
        for (PsiAnnotation annotation : evaluator.getAllAnnotations(owner, true)) {
            String signature = annotation.getQualifiedName();
            if (signature == null) {
                continue;
            }
            if (signature.equals(COLOR_INT_ANNOTATION)) {
                return EnumSet.of(COLOR_INT_MARKER_TYPE);
            }
            if (signature.equals(PX_ANNOTATION)) {
                return EnumSet.of(PX_MARKER_TYPE);
            }
            if (signature.endsWith(RES_SUFFIX)
                    && signature.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
                String typeString = signature
                        .substring(SUPPORT_ANNOTATIONS_PREFIX.length(),
                                signature.length() - RES_SUFFIX.length())
                        .toLowerCase(Locale.US);
                ResourceType type = ResourceType.getEnum(typeString);
                if (type != null) {
                    return EnumSet.of(type);
                } else if (typeString.equals("any")) { // @AnyRes
                    return getAnyRes();
                }
            }
        }

        return null;
    }

    /** Returns a resource URL based on the field reference in the code */
    @Nullable
    public static ResourceUrl getResourceConstant(@NonNull PsiElement node) {
        // R.type.name
        if (node instanceof PsiReferenceExpression) {
            PsiReferenceExpression expression = (PsiReferenceExpression) node;
            if (expression.getQualifier() instanceof PsiReferenceExpression) {
                PsiReferenceExpression select = (PsiReferenceExpression) expression.getQualifier();
                if (select.getQualifier() instanceof PsiReferenceExpression) {
                    PsiReferenceExpression reference = (PsiReferenceExpression) select
                            .getQualifier();
                    if (R_CLASS.equals(reference.getReferenceName())) {
                        String typeName = select.getReferenceName();
                        String name = expression.getReferenceName();

                        ResourceType type = ResourceType.getEnum(typeName);
                        if (type != null && name != null) {
                            boolean isFramework =
                                    reference.getQualifier() instanceof PsiReferenceExpression
                                            && ANDROID_PKG
                                            .equals(((PsiReferenceExpression) reference.
                                                    getQualifier()).getReferenceName());

                            return ResourceUrl.create(type, name, isFramework, false);
                        }
                    }
                }
            }
        } else if (node instanceof PsiField) {
            PsiField field = (PsiField) node;
            PsiClass typeClass = field.getContainingClass();
            if (typeClass != null) {
                PsiClass rClass = typeClass.getContainingClass();
                if (rClass != null && R_CLASS.equals(rClass.getName())) {
                    String name = field.getName();
                    ResourceType type = ResourceType.getEnum(typeClass.getName());
                    if (type != null && name != null) {
                        String qualifiedName = rClass.getQualifiedName();
                        boolean isFramework = qualifiedName != null
                                && qualifiedName.startsWith(ANDROID_PKG_PREFIX);
                        return ResourceUrl.create(type, name, isFramework, false);
                    }
                }
            }
        }
        return null;
    }

    private static EnumSet<ResourceType> getAnyRes() {
        EnumSet<ResourceType> types = EnumSet.allOf(ResourceType.class);
        types.remove(ResourceEvaluator.COLOR_INT_MARKER_TYPE);
        types.remove(ResourceEvaluator.PX_MARKER_TYPE);
        return types;
    }
}
