/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.emptyActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.common.recipeManifest
import com.android.tools.idea.wizard.template.impl.common.recipeSimpleLayout
import com.android.tools.idea.wizard.template.impl.emptyActivity.src.emptyActivityKt
import com.android.tools.idea.wizard.template.impl.emptyActivity.src.emptyActivityWithCppSupportKt
import com.android.tools.idea.wizard.template.impl.emptyActivity.src.nativeLibCpp

fun RecipeExecutor.emptyActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  generateLayout: Boolean,
  layoutName: String,
  isLauncher: Boolean,
  packageName: PackageName,
  includeCppSupport: Boolean = false
) {
  val (projectData, srcOut) = moduleData
  val useAndroidX = projectData.androidXSupport
  val useMaterial2 = useAndroidX || hasDependency("com.google.android.material:material")
  val ktOrJavaExt = projectData.language.extension

  recipeManifest(
    moduleData , activityClass, "", packageName, isLauncher, false,
    requireTheme = false, generateActivityTitle = false, useMaterial2 = useMaterial2
  )

  addAllKotlinDependencies(moduleData)

  if (generateLayout || includeCppSupport) {
    recipeSimpleLayout(moduleData, activityClass, layoutName, true, packageName)
  }

  // TODO(b/142690180)
  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")
  if (includeCppSupport) {
    val nativeSrcOut = moduleData.rootDir.resolve("src/main/cpp")
    save(
      emptyActivityWithCppSupportKt(packageName, activityClass, layoutName, useAndroidX),
      simpleActivityPath
    )
    save(nativeLibCpp(packageName, activityClass), nativeSrcOut.resolve("native-lib.cpp"))
  } else {
    val simpleActivity = emptyActivityKt(packageName, activityClass, layoutName, generateLayout, useAndroidX)
    save(simpleActivity, simpleActivityPath)
  }

  open(simpleActivityPath)
}
