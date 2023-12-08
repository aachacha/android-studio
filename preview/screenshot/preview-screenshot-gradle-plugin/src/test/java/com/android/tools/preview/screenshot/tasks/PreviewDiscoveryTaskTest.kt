/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.preview.screenshot.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewDiscoveryTaskTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    lateinit var task: PreviewDiscoveryTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirRule.newFolder()).build()
        task = project.tasks.create("debugPreviewTest", PreviewDiscoveryTask::class.java)
    }

    @Test
    fun testPreviewDiscovery() {
        val resultsDir = tempDirRule.newFolder("results")
        val referenceImageDir = tempDirRule.newFolder("references")
        task.resultsDir.set(resultsDir)
        task.referenceImageDir.set(referenceImageDir)
        task.previewsOutputFile.set(tempDirRule.newFile("previews_discovered.json"))

        task.run()

        assert(resultsDir.isDirectory)
        assert(referenceImageDir.isDirectory)
    }

}