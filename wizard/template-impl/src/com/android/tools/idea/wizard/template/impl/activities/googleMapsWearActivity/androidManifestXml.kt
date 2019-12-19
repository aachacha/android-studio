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

package com.android.tools.idea.wizard.template.impl.activities.googleMapsWearActivity

import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.renderIf


fun androidManifestXml(
  activityClass: String,
  isLauncher: Boolean,
  isLibrary: Boolean,
  isNew: Boolean,
  packageName: String
): String {
  val labelBlock = if (isNew) "android:label=\"@string/app_name\""
  else "android:label=\"@string/title_${activityToLayout(activityClass)}\""
  val intentFilterBlock = renderIf(isLauncher && !isLibrary) {"""
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
  """
  }
  return """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>

        <!-- Set to true if app can function without mobile companion app. -->
        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <uses-library
            android:name="com.google.android.wearable"
            android:required="false" />

        <!--
             The API key for Google Maps-based APIs is defined as a string resource.
             (See the file "res/values/google_maps_api.xml").
             Note that the API key is linked to the encryption key used to sign the APK.
             You need a different API key for each encryption key, including the release key that is used to
             sign the APK for publishing.
             You can define the keys for the debug and release targets in src/debug/ and src/release/.
         -->
        <meta-data android:name="com.google.android.geo.API_KEY" android:value="@string/google_maps_key"/>

        <activity android:name="${packageName}.${activityClass}"
            $labelBlock>
            $intentFilterBlock
        </activity>
    </application>

</manifest>
"""
}
