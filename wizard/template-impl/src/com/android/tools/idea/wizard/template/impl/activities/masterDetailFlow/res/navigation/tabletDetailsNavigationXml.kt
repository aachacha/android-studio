/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.masterDetailFlow.res.navigation


fun tabletDetailsNavigationXml(
  packageName: String,
  detailName: String,
  detailNameLayout: String
) = """
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/nav_graph_details"
    app:startDestination="@id/fragment_${detailNameLayout}">
    <fragment
        android:id="@+id/fragment_${detailNameLayout}"
        android:name="${packageName}.${detailName}Fragment"
        android:label="@string/title_${detailNameLayout}"
        tools:layout="@layout/fragment_${detailNameLayout}">
        <argument
            android:name="item_id"
            app:argType="string"
            android:defaultValue="" />
    </fragment>

</navigation>
"""