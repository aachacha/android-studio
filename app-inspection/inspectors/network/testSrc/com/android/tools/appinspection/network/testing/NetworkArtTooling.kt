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

package com.android.tools.appinspection.network.testing

import android.app.Application
import android.content.pm.ApplicationInfo
import com.android.tools.appinspection.common.FakeArtTooling

class NetworkArtTooling : FakeArtTooling() {
  override fun <T> findInstances(clazz: Class<T>): List<T> {
    return if (clazz.name == Application::class.java.name) {
      @Suppress("UNCHECKED_CAST")
      listOf(Application(), FakeApplication()) as List<T>
    } else {
      emptyList()
    }
  }

  private class FakeApplication() : Application() {

    override fun getApplicationInfo(): ApplicationInfo {
      return ApplicationInfo()
    }
  }
}