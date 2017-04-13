/*
 * Copyright (C) 2016 The Android Open Source Project
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
#ifndef MEMORY_AGENT_H
#define MEMORY_AGENT_H

#include "jvmti.h"

#include <unistd.h>
#include <atomic>
#include <mutex>
#include <unordered_map>
#include <vector>

#include "proto/internal_memory.grpc.pb.h"
#include "proto/memory.grpc.pb.h"
#include "utils/clock.h"

using profiler::Clock;
using profiler::proto::MemoryControlRequest;
using profiler::proto::AllocationEvent;

namespace profiler {

class MemoryAgent {
 public:
  static MemoryAgent* Instance(JavaVM* vm);

 private:
  explicit MemoryAgent(jvmtiEnv* jvmti);

  // Agent is alive through the app's lifetime, don't bother cleaning up.
  ~MemoryAgent() = delete;
  MemoryAgent(const MemoryAgent&) = delete;
  MemoryAgent& operator=(const MemoryAgent&) = delete;

  void Initialize();
  void StartLiveTracking();
  void StopLiveTracking();
  void RegisterNewClass(JNIEnv* jni, jclass klass, AllocationEvent* event);
  void LogGcStart();
  void LogGcFinish();

  long GetNextClassTag() { return current_class_tag_++; }
  long GetNextObjectTag() { return current_object_tag_++; }

  void HandleControlSignal(const MemoryControlRequest* request);

  // An heap walker used for setting up an initial snapshot of live objects.
  static jint JNICALL HeapIterationCallback(jlong class_tag, jlong size,
                                            jlong* tag_ptr, jint length,
                                            void* user_data);

  // JVMTI Callback for when a class object is ready.
  static void JNICALL ClassPrepareCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                           jthread thread, jclass klass);
  // JVMTI Callback for object allocation events
  static void JNICALL ObjectAllocCallback(jvmtiEnv* jvmti, JNIEnv* jni,
                                          jthread thread, jobject object,
                                          jclass object_klass, jlong size);
  // JVMTI Callback for object free events.
  static void JNICALL ObjectFreeCallback(jvmtiEnv* jvmti, jlong tag);
  // JVMTI Callback for garbage collection start events.
  static void JNICALL GCStartCallback(jvmtiEnv* jvmti);
  // JVMTI Callback for garbage collection end events.
  static void JNICALL GCFinishCallback(jvmtiEnv* jvmti);

  SteadyClock clock_;

  jvmtiEnv* jvmti_;
  bool is_live_tracking_;
  int32_t app_id_;
  int64_t last_tracking_start_ns_;
  int64_t last_gc_start_ns_;
  std::atomic<long> current_class_tag_;
  std::atomic<long> current_object_tag_;

  std::mutex class_data_mutex_;
  std::unordered_map<std::string, long> class_tag_map_;
  std::vector<jobject> class_global_refs_;
  std::vector<AllocationEvent::Klass> class_data_;
};

}  // namespace profiler

#endif  // MEMORY_AGENT_H
