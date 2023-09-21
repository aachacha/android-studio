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

package com.android.build.gradle.internal.testing.screenshot

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.assertTrue

class SaveResultsUtilTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()
    private val goldenPath: Path = Path.of("goldenPath")
    private val actualPath: Path = Path.of("actualPath")
    private val diffPath: Path = Path.of("diffPath")

    private lateinit var previewResults: List<PreviewResult>

    private fun createPreviewResultSuccess(): PreviewResult {
        return PreviewResult(0, "package.previewTest1", "Golden saved", ImageDetails(goldenPath, null))
    }

    private fun createPreviewResultFailed(): PreviewResult {
        return PreviewResult(1, "package.previewTest2", "Images don't match", ImageDetails(goldenPath, null),
            ImageDetails(actualPath, null), ImageDetails(diffPath, null)
        )
    }

    private fun createPreviewResultError(): PreviewResult {
        return PreviewResult(2, "package.previewTest3", "Render error",
            ImageDetails(goldenPath, null), ImageDetails(null, "Render error: Class XYZ not found"))
    }

    @Test
    fun testSaveResults() {
        previewResults = listOf(createPreviewResultSuccess(), createPreviewResultSuccess(),
            createPreviewResultError(), createPreviewResultFailed())
        val file = saveResults(previewResults, tempDirRule.root.toPath()).toFile()
        assertTrue(file.exists())
        val fileContent = javaClass.getResourceAsStream("results.xml")?.readBytes()?.toString(Charsets.UTF_8)
        val expectedContent = fileContent!!.replace("goldenPath", goldenPath.toString())
            .replace("actualPath", actualPath.toString())
            .replace("diffPath", diffPath.toString())
        assertThat(file.readText()).isEqualTo(expectedContent)

    }
}