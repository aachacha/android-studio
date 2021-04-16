/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.other.appWidget.res.drawable_v21

fun appWidgetInnerViewBackgroundXml() = """
<?xml version="1.0" encoding="utf-8"?>
<!--
Background for views inside widgets to make the rounded corners based on the
appWidgetInnerRadius attribute value
-->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">

    <corners android:radius="?attr/appWidgetInnerRadius" />
    <solid android:color="?android:attr/colorAccent" />
</shape>
"""
