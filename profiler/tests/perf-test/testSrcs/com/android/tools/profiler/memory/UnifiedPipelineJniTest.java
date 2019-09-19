/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.memory;

import com.android.tools.fakeandroid.FakeAndroidDriver;
import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.TransportStubWrapper;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationEvent;
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashSet;

import static com.android.tools.profiler.memory.UnifiedPipelineMemoryTestUtils.findClassTag;
import static com.android.tools.profiler.memory.UnifiedPipelineMemoryTestUtils.startAllocationTracking;
import static com.google.common.truth.Truth.assertThat;

public class UnifiedPipelineJniTest {
    private static final String ACTIVITY_CLASS = "com.activity.NativeCodeActivity";

    // We currently only test O+ test scenarios.
    @Rule
    public PerfDriver myPerfDriver = new PerfDriver(ACTIVITY_CLASS, 26, true);
    private GrpcUtils myGrpc;
    private TransportStubWrapper myTransportWrapper;
    private FakeAndroidDriver myAndroidDriver;

    @Before
    public void setup() {
        myGrpc = myPerfDriver.getGrpc();
        myAndroidDriver = myPerfDriver.getFakeAndroidDriver();
        myTransportWrapper = new TransportStubWrapper(myGrpc.getTransportAsyncStub());
    }

    private void validateMemoryMap(Memory.MemoryMap map, Memory.NativeBacktrace backtrace) {
        assertThat(backtrace.getAddressesList().isEmpty()).isFalse();
        assertThat(map.getRegionsList().isEmpty()).isFalse();
        for (long addr : backtrace.getAddressesList()) {
            boolean found = false;
            for (Memory.MemoryMap.MemoryRegion region : map.getRegionsList()) {
                if (region.getStartAddress() <= addr && region.getEndAddress() > addr) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    public void countCreatedAndDeleteRefEvents() throws Exception {
        // Find JNITestEntity class tag
        int[] testEntityIdFinal = new int[1];
        myTransportWrapper.getEvents(
                event -> {
                    int id = findClassTag(event.getMemoryAllocContexts().getContexts(), "JNITestEntity");
                    testEntityIdFinal[0] = id;
                    return id != 0;
                },
                event -> event.getKind() == Common.Event.Kind.MEMORY_ALLOC_CONTEXTS,
                (unused) -> startAllocationTracking(myPerfDriver));
        int testEntityId = testEntityIdFinal[0];
        assertThat(testEntityId).isNotEqualTo(0);

        final int refCount = 10;
        boolean[] allRefsAccounted = new boolean[1];
        int[] refsReported = new int[1];
        Memory.MemoryMap[] lastMemoryMap = new Memory.MemoryMap[1];
        HashSet<Long> refs = new HashSet<>();
        HashSet<Integer> tags = new HashSet<>();
        myTransportWrapper.getEvents(
                event -> {
                    if (event.getKind() == Common.Event.Kind.MEMORY_ALLOC_EVENTS) {
                        for (AllocationEvent evt :
                                event.getMemoryAllocEvents().getEvents().getEventsList()) {
                            if (evt.getEventCase() == AllocationEvent.EventCase.ALLOC_DATA) {
                                AllocationEvent.Allocation alloc = evt.getAllocData();
                                if (alloc.getClassTag() == testEntityId) {
                                    tags.add(alloc.getTag());
                                    System.out.printf(
                                            "Add obj tag: %d, %d\n",
                                            alloc.getTag(), event.getTimestamp());
                                }
                            }
                        }
                    } else if (event.getKind() == Common.Event.Kind.MEMORY_ALLOC_CONTEXTS) {
                        // We always expect a context event to precede a alloc/jni ref event. Cache the memory map
                        // received and use it to validate the backtrace for the next incoming jni ref event.
                        lastMemoryMap[0] =
                                event.getMemoryAllocContexts().getContexts().getMemoryMap();
                    } else if (event.getKind() == Common.Event.Kind.MEMORY_JNI_REF_EVENTS) {
                        for (JNIGlobalReferenceEvent evt :
                                event.getMemoryJniRefEvents().getEvents().getEventsList()) {
                            long refValue = evt.getRefValue();
                            assertThat(evt.getThreadId()).isGreaterThan(0);
                            if (evt.getEventType() == JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF
                                    && tags.contains(evt.getObjectTag())) {
                                System.out.printf(
                                        "Add JNI ref: %d tag:%d %d\n",
                                        refValue, evt.getObjectTag(), event.getTimestamp());
                                String refRelatedOutput =
                                        String.format("JNI ref created %d", refValue);
                                assertThat(myAndroidDriver.waitForInput(refRelatedOutput)).isTrue();
                                assertThat(refs.add(refValue)).isTrue();
                                refsReported[0]++;
                            }
                            if (evt.getEventType() == JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF
                                    // Test that reference value was reported when created
                                    && refs.contains(refValue)) {
                                System.out.printf(
                                        "Remove JNI ref: %d tag:%d %d\n",
                                        refValue, evt.getObjectTag(), event.getTimestamp());
                                String refRelatedOutput =
                                        String.format("JNI ref deleted %d", refValue);
                                assertThat(myAndroidDriver.waitForInput(refRelatedOutput)).isTrue();
                                assertThat(tags.contains(evt.getObjectTag())).isTrue();
                                refs.remove(refValue);
                                if (refs.isEmpty() && refsReported[0] == refCount) {
                                    allRefsAccounted[0] = true;
                                }
                            }
                            validateMemoryMap(lastMemoryMap[0], evt.getBacktrace());
                        }
                    }

                    return allRefsAccounted[0];
                },
                event ->
                        event.getKind() == Common.Event.Kind.MEMORY_ALLOC_EVENTS
                                || event.getKind() == Common.Event.Kind.MEMORY_JNI_REF_EVENTS
                                || event.getKind() == Common.Event.Kind.MEMORY_ALLOC_CONTEXTS,
                (unused) -> {
                    myAndroidDriver.setProperty("jni.refcount", Integer.toString(refCount));
                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "createRefs");
                    assertThat(myAndroidDriver.waitForInput("createRefs")).isTrue();

                    myAndroidDriver.triggerMethod(ACTIVITY_CLASS, "deleteRefs");
                    assertThat(myAndroidDriver.waitForInput("deleteRefs")).isTrue();
                });

        assertThat(allRefsAccounted[0]).isTrue();
    }
}
