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

package com.android.build.api.apiTest.groovy

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class GetApksTest: VariantApiBaseTest(TestType.Script, ScriptingLanguage.Groovy) {
    @Test
    fun getApksTest() {
        given {
            tasksToInvoke.add(":app:debugDisplayApks")

            addModule(":app") {
                buildFile = """
            plugins {
                id 'com.android.application'
            }

            ${testingElements.getDisplayApksTask()}

            android {
                ${testingElements.addCommonAndroidBuildLogic()}

                onVariantProperties {
                    project.tasks.register(it.getName() + "DisplayApks", DisplayApksTask.class) {
                        it.apkFolder.set(artifacts.get(ArtifactType.APK.INSTANCE))
                        it.builtArtifactsLoader.set(artifacts.getBuiltArtifactsLoader())
                    }
                }
            }
                """.trimIndent()

                testingElements.addManifest(this)
            }
        }

        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Got an APK")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}