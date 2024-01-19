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

package com.android.tools.preview.screenshot.junit.engine

import java.util.Optional
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.MethodSelector
import org.junit.platform.engine.discovery.UniqueIdSelector
import org.junit.platform.engine.support.discovery.SelectorResolver
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.engine.support.discovery.SelectorResolver.Context
import org.junit.platform.engine.support.discovery.SelectorResolver.Resolution
import org.junit.platform.engine.support.discovery.SelectorResolver.Match

internal class MethodSelectorResolver(private val tests: Tests) : SelectorResolver {

    override fun resolve(selector: MethodSelector, context: Context): Resolution {
        return resolve(selector.getClassName(), selector.getMethodName(), context)
    }

    override fun resolve(selector: UniqueIdSelector, context: Context): Resolution {
        val lastSegment: UniqueId.Segment = selector.getUniqueId().getLastSegment()
        if (SEGMENT_TYPE == lastSegment.getType() && selector.getUniqueId()
                .getSegments()
                .size >= 2
        ) {
            val className: String =
                selector.getUniqueId().removeLastSegment().getLastSegment().getValue()
            val methodName: String = lastSegment.getValue()
            return resolve(className, methodName, context)
        }
        return Resolution.unresolved()
    }

    private fun resolve(className: String, methodName: String, context: Context): Resolution {
        return if (tests.getMethods(className).contains(methodName)) {
            context.addToParent({ selectClass(className) }) { parent ->
                createTestMethodDescriptor(
                    className,
                    methodName,
                    parent
                )
            }
                .map(Match::exact)
                .map(Resolution::match)
                .orElseGet(Resolution::unresolved)
        } else Resolution.unresolved()
    }

    private fun createTestMethodDescriptor(
        className: String,
        methodName: String,
        parent: TestDescriptor
    ): Optional<TestMethodDescriptor> {
        val uniqueId: UniqueId = parent.getUniqueId().append(SEGMENT_TYPE, methodName)
        return Optional.of<TestMethodDescriptor>(
            TestMethodDescriptor(
                uniqueId,
                methodName,
                className
            )
        )
    }

    companion object {
        private const val SEGMENT_TYPE = "test"
    }
}