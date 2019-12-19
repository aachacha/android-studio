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

package com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.res.values


fun dimensXml() = """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
    Because the window insets on round devices are larger than 15dp, this padding only applies
    to square screens.
    -->
    <dimen name="box_inset_layout_padding">0dp</dimen>

    <!--
    This padding applies to both square and round screens. The total padding between the buttons
    and the window insets is box_inset_layout_padding (above variable) on square screens and
    inner_frame_layout_padding (below variable) on round screens.
    -->
    <dimen name="inner_frame_layout_padding">5dp</dimen>
</resources>
"""
