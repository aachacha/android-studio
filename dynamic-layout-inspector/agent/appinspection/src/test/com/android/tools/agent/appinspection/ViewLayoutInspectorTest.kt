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

package com.android.tools.agent.appinspection

import android.content.Context
import android.content.res.Resources
import android.graphics.Picture
import android.view.View
import android.view.WindowManagerGlobal
import com.android.tools.agent.appinspection.testutils.FrameworkStateRule
import com.android.tools.agent.appinspection.testutils.MainLooperRule
import com.android.tools.agent.appinspection.testutils.inspection.InspectorRule
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.google.common.truth.Truth.assertThat
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue

class ViewLayoutInspectorTest {

    @get:Rule
    val mainLooperRule = MainLooperRule()

    @get:Rule
    val inspectorRule = InspectorRule()

    @get:Rule
    val frameworkRule = FrameworkStateRule()

    @Test
    fun canStartAndStopInspector() = createViewInspector { viewInspector ->

        val responseQueue = ArrayBlockingQueue<ByteArray>(1)
        inspectorRule.commandCallback.replyListeners.add { bytes ->
            responseQueue.offer(bytes)
        }

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.START_FETCH_RESPONSE)
        }

        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )
        responseQueue.take().let { bytes ->
            val response = Response.parseFrom(bytes)
            assertThat(response.specializedCase).isEqualTo(Response.SpecializedCase.STOP_FETCH_RESPONSE)
        }
    }

    @Test
    fun canCaptureTreeInContinuousMode() = createViewInspector { viewInspector ->

        val eventQueue = ArrayBlockingQueue<ByteArray>(2)
        inspectorRule.connection.eventListeners.add { bytes ->
            eventQueue.offer(bytes)
        }

        val resourceNames = mutableMapOf<Int, String>()
        val resources = Resources(resourceNames)
        val context = Context("view.inspector.test", resources)
        val tree1 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree2 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        val tree3 = View(context).apply { setAttachInfo(View.AttachInfo() )}
        WindowManagerGlobal.instance.rootViews.addAll(listOf(tree1, tree2))

        val startFetchCommand = Command.newBuilder().apply {
            startFetchCommandBuilder.apply {
                continuous = true
            }
        }.build()
        viewInspector.onReceiveCommand(
            startFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for startCommand to finish initializing

        assertThat(eventQueue).isEmpty()

        val tree1FakePicture1 = Picture(byteArrayOf(1, 1))
        val tree1FakePicture2 = Picture(byteArrayOf(1, 2))
        val tree1FakePicture3 = Picture(byteArrayOf(1, 3))
        val tree1FakePicture4 = Picture(byteArrayOf(1, 4))
        val tree2FakePicture = Picture(byteArrayOf(2))
        val tree3FakePicture = Picture(byteArrayOf(3))

        tree1.forcePictureCapture(tree1FakePicture1)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId
            )
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture1.bytes)
            }
        }

        tree2.forcePictureCapture(tree2FakePicture)
        // Roots event not resent, as roots haven't changed
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree2.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree2FakePicture.bytes)
            }
        }

        WindowManagerGlobal.instance.rootViews.add(tree3)

        tree1.forcePictureCapture(tree1FakePicture2)
        // As a side-effect, this capture discovers the newly added third tree
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree2.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture2.bytes)
            }
        }

        // Roots changed - this should generate a new roots event
        WindowManagerGlobal.instance.rootViews.remove(tree2)
        tree1.forcePictureCapture(tree1FakePicture3)

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.ROOTS_EVENT)
            assertThat(event.rootsEvent.idsList).containsExactly(
                tree1.uniqueDrawingId,
                tree3.uniqueDrawingId
            )
        }

        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture3.bytes)
            }
        }

        val stopFetchCommand = Command.newBuilder().apply {
            stopFetchCommand = StopFetchCommand.getDefaultInstance()
        }.build()
        viewInspector.onReceiveCommand(
            stopFetchCommand.toByteArray(),
            inspectorRule.commandCallback
        )

        ThreadUtils.runOnMainThread { }.get() // Wait for the stop command to run its course

        // Normally, stopping the inspector triggers invalidate calls, but in fake android, those
        // do nothing. Instead, we emulate this by manually firing capture events.
        tree1.forcePictureCapture(tree1FakePicture4)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree1.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree1FakePicture4.bytes)

            }
        }
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            assertThat(event.propertiesEvent.rootId).isEqualTo(tree1.uniqueDrawingId)
        }

        tree3.forcePictureCapture(tree3FakePicture)
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.LAYOUT_EVENT)
            event.layoutEvent.let { layoutEvent ->
                assertThat(layoutEvent.rootView.id).isEqualTo(tree3.uniqueDrawingId)
                assertThat(layoutEvent.screenshot.bytes.toByteArray()).isEqualTo(tree3FakePicture.bytes)

            }
        }
        eventQueue.take().let { bytes ->
            val event = Event.parseFrom(bytes)
            assertThat(event.specializedCase).isEqualTo(Event.SpecializedCase.PROPERTIES_EVENT)
            assertThat(event.propertiesEvent.rootId).isEqualTo(tree3.uniqueDrawingId)
        }
    }

    // TODO: Add test for testing snapshot mode (which will require adding more support for fetching
    //  view properties in fake-android.

    // TODO: Add test for filtering system views and properties

    private fun createViewInspector(block: (ViewLayoutInspector) -> Unit) {
        // We could just create the view inspector directly, but using the factory mimics what
        // actually happens in production.
        val factory = ViewLayoutInspectorFactory()
        val viewInspector =
            factory.createInspector(inspectorRule.connection, inspectorRule.environment)
        block(viewInspector)
        viewInspector.onDispose()
    }
}
