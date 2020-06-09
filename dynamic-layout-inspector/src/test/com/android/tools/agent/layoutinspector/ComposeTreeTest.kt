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

package com.android.tools.agent.layoutinspector

import android.util.Log
import android.widget.RootLinearLayout
import androidx.ui.core.AndroidComposeView
import androidx.ui.tooling.inspector.InspectorNode
import androidx.ui.tooling.inspector.TREE_ENTRY
import com.android.tools.agent.layoutinspector.testing.StandardView
import com.android.tools.agent.layoutinspector.testing.StringTable
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`

class ComposeTreeTest {

    private val root: InspectorNode =
        InspectorNode(
            id = 77,
            name = "Column",
            fileName = "MainActivity.kt",
            lineNumber = 26,
            functionName = "com.example.mycompose.MainActivity\$onCreate$1$1.invoke",
            left = 55,
            top = 121,
            width = 421,
            height = 269,
            parameters = emptyList(),
            children = listOf(
                InspectorNode(
                    id = -2,
                    name = "Text",
                    fileName = "MainActivity.kt",
                    lineNumber = 27,
                    functionName = "com.example.mycompose.MainActivity\$onCreate$1$1$1.invoke",
                    left = 55,
                    top = 121,
                    width = 189,
                    height = 111,
                    parameters = emptyList(),
                    children = emptyList()
                ),
                InspectorNode(
                    id = -3,
                    name = "Button",
                    fileName = "MainActivity.kt",
                    lineNumber = 36,
                    functionName = "com.example.mycompose.MainActivity\$onCreate$1$1$1.invoke",
                    left = 55,
                    top = 291,
                    width = 176,
                    height = 269,
                    parameters = emptyList(),
                    children = emptyList()
                )
            )
        )

    @Test
    fun testSimpleComposeExample() {
        val linearLayout = StandardView.createLinearLayoutWithComposeView()
        val androidComposeView = linearLayout.getChildAt(0)
        `when`(androidComposeView.getTag(eq(TREE_ENTRY))).thenReturn(listOf(root))

        System.loadLibrary("jni-test")
        val event = ComponentTreeTest.allocateEvent()
        val properties = Properties()
        val treeBuilder = ComponentTree(properties, true)
        treeBuilder.writeTree(event, linearLayout)

        val proto = ComponentTreeEvent.parseFrom(ComponentTreeTest.toByteArray(event))
        val table = StringTable(proto.stringList)
        val layout = proto.root
        assertThat(table[layout.className]).isEqualTo(RootLinearLayout::class.java.simpleName)
        assertThat(layout.subViewCount).isEqualTo(1)
        val view = layout.subViewList.first()
        assertThat(table[view.className]).isEqualTo(AndroidComposeView::class.java.simpleName)
        assertThat(view.subViewCount).isEqualTo(1)
        checkComposeView(table, view.subViewList.first(), root)
    }

    @Test
    fun testDebugFlagsAreOffForCheckIn() {
        assertThat(Log.DEBUG_LOG_IN_TESTS).isFalse()
    }

    private fun checkComposeView(
        table: StringTable,
        view: LayoutInspectorProto.View,
        expected: InspectorNode
    ) {
        val name = "Mismatch for compose node: ${expected.name}"
        assertThat(table[view.className]).isEqualTo(expected.name)
        val actualInvocation = "${table[view.composePackage]}.${table[view.composeInvocation]}"
        assertThat(actualInvocation).named(name).isEqualTo(expected.functionName)
        assertThat(view.drawId).named(name).isEqualTo(expected.id)
        assertThat(table[view.composeFilename]).named(name).isEqualTo(expected.fileName)
        assertThat(view.composeLineNumber).named(name).isEqualTo(expected.lineNumber)
        assertThat(view.x).named(name).isEqualTo(expected.left)
        assertThat(view.y).named(name).isEqualTo(expected.top)
        assertThat(view.width).named(name).isEqualTo(expected.width)
        assertThat(view.height).named(name).isEqualTo(expected.height)
        assertThat(view.subViewCount).named(name).isEqualTo(expected.children.size)
        for (index in view.subViewList.indices) {
            checkComposeView(table, view.getSubView(index), expected.children[index])
        }
    }
}
