/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.compose

import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.tools.render.compose.readComposeRenderingResultJson
import com.android.tools.render.compose.ComposeScreenshot
import com.android.tools.render.compose.ComposeScreenshotResult
import java.io.File
import javax.imageio.ImageIO
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.discovery.EngineDiscoveryRequestResolver

class PreviewScreenshotTestEngine : TestEngine {

    override fun getId(): String {
        return "preview-screenshot-test-engine"
    }

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val engineDescriptor = EngineDescriptor(uniqueId, "Preview Screenshot Test Engine")
        val screenshots: List<ComposeScreenshot> = readComposeScreenshotsJson(File(getParam("previews-discovered")).reader())
        val testMap = mutableMapOf<String, MutableSet<String>>()
        for (screenshot in screenshots) {
            val methodName = screenshot.methodFQN.split(".").last()
            val className = screenshot.methodFQN.substring(0, screenshot.methodFQN.lastIndexOf("."))
            if (testMap.contains(className)) {
                testMap[className]!!.add(methodName)
            } else {
                testMap[className] = mutableSetOf(methodName)
            }
        }
        val tests = Tests(testMap)

        EngineDiscoveryRequestResolver.builder<EngineDescriptor>()
            .addClassContainerSelectorResolver { testClass ->
                tests.classes.contains(testClass.getName())
            }
            .addSelectorResolver { ctx -> ClassSelectorResolver(ctx.classNameFilter, tests) }
            .addSelectorResolver(MethodSelectorResolver(tests))
            .build()
            .resolve(discoveryRequest, engineDescriptor)
        return engineDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        val listener = request.engineExecutionListener
        val resultsToSave = mutableListOf<PreviewResult>()
        if (request.rootTestDescriptor.children.isEmpty()) return
        val resultFile = File(getParam("renderTaskOutputDirPath")).toPath().resolve("results.json").toFile()
        val screenshotResults = readComposeRenderingResultJson(resultFile.reader()).screenshotResults
        val composeScreenshots: List<ComposeScreenshot> = readComposeScreenshotsJson(File(getParam("previews-discovered")).reader())

        // Method descriptors are created as type CONTAINER_AND_TEST. For single method tests,
        // replace the method descriptor with one of type TEST.
        for (classDescriptor in request.rootTestDescriptor.children) {
            val methodsToRemove = mutableListOf<TestMethodDescriptor>()
            val methodsToAdd = mutableListOf<TestMethodTestDescriptor>()
            for (methodDescriptor in classDescriptor.children) {
                val className: String = (methodDescriptor as TestMethodDescriptor).className
                val methodName: String = methodDescriptor.methodName
                val screenshots =
                    screenshotResults.filter {
                        getResultIdWithoutSuffix(it.resultId) == "$className.${methodName}"
                    }
                if (screenshots.size == 1) {
                    val testMethodDescriptor = TestMethodTestDescriptor(methodDescriptor.uniqueId, methodName, className)
                    methodsToAdd.add(testMethodDescriptor)
                    methodsToRemove.add(methodDescriptor)
                }
            }
            methodsToAdd.forEach {
                classDescriptor.addChild(it)
                listener.dynamicTestRegistered(it)
            }
            methodsToRemove.forEach { classDescriptor.removeChild(it) }
        }

        for (classDescriptor in request.rootTestDescriptor.children) {
            listener.executionStarted(classDescriptor)
            for (methodDescriptor in classDescriptor.children) {
                val methodResults = mutableListOf<PreviewResult>()
                if (methodDescriptor is TestMethodTestDescriptor) {
                    val className: String = methodDescriptor.className
                    val methodName: String = methodDescriptor.methodName
                    val screenshots =
                        screenshotResults.filter {
                            getResultIdWithoutSuffix(it.resultId) == "$className.${methodName}"
                        }
                    methodResults.add(reportResult(listener, screenshots.single(), methodDescriptor, "${className}.${methodName}"))
                } else if (methodDescriptor is TestMethodDescriptor) {
                    listener.executionStarted(methodDescriptor)
                    val className: String = methodDescriptor.className
                    val methodName: String = methodDescriptor.methodName
                    val screenshots =
                        screenshotResults.filter {
                            getResultIdWithoutSuffix(it.resultId) == "$className.${methodName}"
                        }
                    for ((run, screenshot) in screenshots.withIndex()) {
                        val currentComposePreview = composeScreenshots.single() {
                            it.methodFQN == "$className.$methodName" && screenshot.imagePath!!.contains(it.imageName)
                        }
                        var suffix = ""
                        if (currentComposePreview.previewParams.isNotEmpty()) {
                            suffix += "_${currentComposePreview.previewParams}"
                        }
                        if (currentComposePreview.methodParams.isNotEmpty()) {
                            // Method parameters can generate multiple screenshots from one preview,
                            // add the method parameters and the count indicated by the resultId
                            suffix += "_${currentComposePreview.methodParams}_${screenshot.resultId.split("_").last()}"
                        }
                        val previewTestDescriptor = PreviewTestDescriptor(methodDescriptor, methodName, run, suffix)
                        methodDescriptor.addChild(previewTestDescriptor)
                        listener.dynamicTestRegistered(previewTestDescriptor)
                        listener.executionStarted(previewTestDescriptor)
                        methodResults.add(reportResult(listener, screenshot, previewTestDescriptor,"${className}.${methodName}$suffix"))
                    }
                    listener.executionFinished(methodDescriptor, TestExecutionResult.successful())
                }
                resultsToSave.addAll(methodResults)
            }
            listener.executionFinished(classDescriptor, TestExecutionResult.successful())
        }
        if (resultsToSave.isNotEmpty()) {
            saveResults(resultsToSave, "${getParam("resultsDirPath")}/TEST-results.xml")
        }
    }

    private fun compareImages(composeScreenshot: ComposeScreenshotResult, testDisplayName: String, startTime: Long): PreviewResult {
        // TODO(b/296430073) Support custom image difference threshold from DSL or task argument
        val imageDiffer = ImageDiffer.MSSIMMatcher()
        val screenshotName = composeScreenshot.resultId
        val screenshotNamePng = "$screenshotName.png"
        var referencePath = File(getParam("referenceImageDirPath")).toPath().resolve(screenshotNamePng)
        var referenceMessage: String? = null
        val actualPath = File(composeScreenshot.imagePath).toPath()
        var diffPath = File(getParam("diffImageDirPath")).toPath().resolve(screenshotNamePng)
        var diffMessage: String? = null
        var code = 0
        val verifier = Verify(imageDiffer, diffPath)

        //If the CLI tool could not render the preview, return the preview result with the
        //code and message along with reference path if it exists
        if (!actualPath.toFile().exists()) {
            if (!referencePath.toFile().exists()) {
                referencePath = null
                referenceMessage = "Reference image missing"
            }

            return PreviewResult(1,
                composeScreenshot.resultId,
                getDurationInSeconds(startTime),
                "Image render failed",
                referenceImage = ImageDetails(referencePath, referenceMessage),
                actualImage = ImageDetails(null, "Image render failed")
            )

        }

        val result =
            verifier.assertMatchReference(
                referencePath,
                ImageIO.read(actualPath.toFile())
            )
        when (result) {
            is Verify.AnalysisResult.Failed -> {
                code = 1
            }
            is Verify.AnalysisResult.Passed -> {
                if (result.imageDiff.highlights == null) {
                    diffPath = null
                    diffMessage = "Images match!"
                }
            }
            is Verify.AnalysisResult.MissingReference -> {
                referencePath = null
                diffPath = null
                referenceMessage = "Reference image missing"
                diffMessage = "No diff available"
                code = 1
            }
            is Verify.AnalysisResult.SizeMismatch -> {
                diffMessage = result.message
                diffPath = null
                code = 1
            }
        }
        return result.toPreviewResponse(code, testDisplayName,
            getDurationInSeconds(startTime),
            ImageDetails(referencePath, referenceMessage),
            ImageDetails(actualPath, null),
            ImageDetails(diffPath, diffMessage)
        )
    }

    private fun getParam(key: String): String {
        return System.getProperty("com.android.tools.preview.screenshot.junit.engine.${key}")
    }

    private fun getDurationInSeconds(startTimeMillis: Long): Float {
        return (System.currentTimeMillis() - startTimeMillis) / 1000F
    }

    private fun reportResult(listener: EngineExecutionListener, screenshot: ComposeScreenshotResult, testDescriptor: TestDescriptor, testDisplayName: String): PreviewResult {
        val startTime = System.currentTimeMillis()
        listener.executionStarted(testDescriptor)
        val imageComparison = compareImages(screenshot, testDisplayName, startTime)
        val result = if (imageComparison.responseCode != 0) {
            TestExecutionResult.failed(AssertionError(imageComparison.message))
        } else TestExecutionResult.successful()
        listener.executionFinished(testDescriptor, result)
        return imageComparison
    }

    private fun getResultIdWithoutSuffix(resultId: String): String {
        return resultId.substringBeforeLast('_').substringBeforeLast('_').substringBeforeLast('_')
    }
}