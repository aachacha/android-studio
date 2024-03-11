/*
 * Copyright (C) 2019 The Android Open Source Project
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
#include "trace_manager.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include "google/protobuf/util/message_differencer.h"
#include "perfd/common/atrace/fake_atrace.h"
#include "perfd/common/perfetto/fake_perfetto.h"
#include "perfd/common/simpleperf/fake_simpleperf.h"
#include "utils/device_info_helper.h"
#include "utils/fake_clock.h"
#include "utils/fs/memory_file_system.h"
#include "utils/termination_service.h"

using google::protobuf::util::MessageDifferencer;
using profiler::proto::TraceConfiguration;
using profiler::proto::TraceMode;
using profiler::proto::TraceStartStatus;
using profiler::proto::TraceStopStatus;

using std::string;
using testing::DoAll;
using testing::HasSubstr;
using testing::Return;
using testing::SaveArg;
using testing::StartsWith;

namespace profiler {

namespace {
const char* const kAmExecutable = "/aaaaa/system/bin/am";
const char* const kProfileStart = "profile start";
const char* const kProfileStop = "profile stop";
}  // namespace

// A subclass of ActivityManager that we want to test. The only difference is it
// has a public constructor.
class TestActivityManager final : public ActivityManager {
 public:
  explicit TestActivityManager(std::unique_ptr<BashCommandRunner> bash)
      : ActivityManager(std::move(bash)) {}
};

// A mock BashCommandRunner that mocks the execution of command.
// We need the mock to run tests across platforms to examine the commands
// generated by ActivityManager.
class MockBashCommandRunner final : public BashCommandRunner {
 public:
  explicit MockBashCommandRunner(const std::string& executable_path)
      : BashCommandRunner(executable_path) {}
  MOCK_CONST_METHOD2(RunAndReadOutput,
                     bool(const std::string& cmd, std::string* output));
};

class MockAtraceManager final : public AtraceManager {
 public:
  MockAtraceManager()
      : AtraceManager(std::unique_ptr<FileSystem>(new MemoryFileSystem()),
                      &clock_, 50,
                      std::unique_ptr<Atrace>(new FakeAtrace(&clock_))) {}
  MOCK_METHOD(bool, StartProfiling,
              (const std::string&, int, int*, const std::string&, int64_t*),
              (override));

 private:
  SteadyClock clock_;
};

class MockPerfettoManager final : public PerfettoManager {
 public:
  MockPerfettoManager() : PerfettoManager() {}
  MOCK_METHOD(bool, StartProfiling,
              (const std::string&, const std::string&,
               const perfetto::protos::TraceConfig&, const std::string&,
               int64_t*),
              (override));
};

// A subclass of TerminationService that we want to test. The only difference is
// it has a public constructor and destructor.
class TestTerminationService final : public TerminationService {
 public:
  explicit TestTerminationService() = default;
  ~TestTerminationService() = default;
};

// This needs to be a struct to set default visibility to public for functions /
// members of testing::Test
struct TraceManagerTest : testing::Test {
  std::unique_ptr<TraceManager> ConfigureDefaultTraceManager(
      const profiler::proto::DaemonConfig::CpuConfig& config) {
    return std::unique_ptr<TraceManager>(new TraceManager(
        &clock_, config, termination_service_.get(),
        ActivityManager::Instance(),
        std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
            std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
        std::unique_ptr<AtraceManager>(new AtraceManager(
            std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock_, 50,
            std::unique_ptr<Atrace>(new FakeAtrace(&clock_, false)))),
        std::unique_ptr<PerfettoManager>(new PerfettoManager(
            std::unique_ptr<Perfetto>(new FakePerfetto())))));
  }

  // Helper function to run atrace test.
  void RunAtraceTest(int feature_level) {
    DeviceInfoHelper::SetDeviceInfo(feature_level);

    profiler::proto::DaemonConfig::CpuConfig config;
    std::unique_ptr<TraceManager> trace_manager =
        ConfigureDefaultTraceManager(config);

    // Start an atrace recording.
    TraceConfiguration configuration;
    configuration.set_app_name("fake_app");
    auto atrace_options = configuration.mutable_atrace_options();
    atrace_options->set_buffer_size_in_mb(8);

    TraceStartStatus start_status;

    auto* capture =
        trace_manager->StartCapture(0, configuration, &start_status);

    // Expect a success result.
    EXPECT_NE(capture, nullptr);
    EXPECT_EQ(start_status.status(), TraceStartStatus::SUCCESS);
    EXPECT_EQ(start_status.error_code(),
              TraceStartStatus::NO_ERROR_TRACE_START);

    // Validate state.
    EXPECT_TRUE(trace_manager->atrace_manager()->IsProfiling());

    // Stop profiling.
    // Test does not validate trace output so we don't need to wait for trace
    // file.
    TraceStopStatus stop_status;
    capture = trace_manager->StopCapture(1, "fake_app", false, &stop_status);
    EXPECT_NE(capture, nullptr);
    EXPECT_EQ(stop_status.status(), TraceStopStatus::SUCCESS);
    EXPECT_EQ(stop_status.error_code(), TraceStopStatus::NO_ERROR_TRACE_STOP);

    // Validate state.
    EXPECT_FALSE(trace_manager->atrace_manager()->IsProfiling());

    // This needs to happen otherwise the termination handler attempts to call
    // shutdown on the TraceManager which causes a segfault.
    termination_service_.reset(nullptr);
  }

  // Helper function to run perfetto tests.
  void RunPerfettoTest(int feature_level) {
    DeviceInfoHelper::SetDeviceInfo(feature_level);

    profiler::proto::DaemonConfig::CpuConfig config;
    std::unique_ptr<TraceManager> trace_manager =
        ConfigureDefaultTraceManager(config);

    // Start an atrace recording.
    TraceConfiguration configuration;
    configuration.set_app_name("fake_app");
    auto perfetto_options = configuration.mutable_perfetto_options();
    perfetto_options->add_buffers()->set_size_kb(8 * 1024);

    TraceStartStatus start_status;

    auto* capture =
        trace_manager->StartCapture(0, configuration, &start_status);

    // Expect a success result.
    EXPECT_NE(capture, nullptr);
    EXPECT_EQ(start_status.status(), TraceStartStatus::SUCCESS);
    EXPECT_EQ(start_status.error_code(),
              TraceStartStatus::NO_ERROR_TRACE_START);

    // Validate state.
    EXPECT_TRUE(trace_manager->perfetto_manager()->IsProfiling());

    // Stop profiling.
    // Test does not validate trace output so we don't need to wait for trace
    // file.
    TraceStopStatus stop_status;
    capture = trace_manager->StopCapture(1, "fake_app", false, &stop_status);
    EXPECT_NE(capture, nullptr);
    EXPECT_EQ(stop_status.status(), TraceStopStatus::SUCCESS);
    EXPECT_EQ(stop_status.error_code(), TraceStopStatus::NO_ERROR_TRACE_STOP);

    // Validate state.
    EXPECT_FALSE(trace_manager->perfetto_manager()->IsProfiling());

    // This needs to happen otherwise the termination handler attempts to call
    // shutdown on the TraceManager which causes a segfault.
    termination_service_.reset(nullptr);
  }

  FakeClock clock_;
  std::unique_ptr<TestTerminationService> termination_service_{
      new TestTerminationService()};
};

TEST_F(TraceManagerTest, StopSimpleperfTraceWhenDaemonTerminated) {
  profiler::proto::DaemonConfig::CpuConfig config;
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config);

  // Start a Simpleperf recording.
  TraceConfiguration configuration;
  configuration.set_app_name("fake_app");
  auto simpleperf_options = configuration.mutable_simpleperf_options();

  TraceStartStatus start_status;

  auto* capture = trace_manager->StartCapture(0, configuration, &start_status);

  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(start_status.status(), TraceStartStatus::SUCCESS);

  // Now, verify that no command has been issued to kill simpleperf.
  auto* fake_simpleperf = dynamic_cast<FakeSimpleperf*>(
      trace_manager->simpleperf_manager()->simpleperf());
  EXPECT_FALSE(fake_simpleperf->GetKillSimpleperfCalled());
  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
  // Now, verify that command to kill simpleperf has been issued.
  EXPECT_TRUE(fake_simpleperf->GetKillSimpleperfCalled());
}

TEST_F(TraceManagerTest, StopArtTraceWhenDaemonTerminated) {
  // Set up test Activity Manager
  string trace_path;
  string output_string;
  string cmd_1, cmd_2;
  std::unique_ptr<BashCommandRunner> bash{
      new MockBashCommandRunner(kAmExecutable)};
  EXPECT_CALL(
      *(static_cast<MockBashCommandRunner*>(bash.get())),
      RunAndReadOutput(testing::A<const string&>(), testing::A<string*>()))
      .Times(2)
      .WillOnce(DoAll(SaveArg<0>(&cmd_1), Return(true)))
      .WillOnce(DoAll(SaveArg<0>(&cmd_2), Return(true)));

  // This test requires a customized ActivityManager instead of using the
  // default as such we construct the TraceManager below.
  TestActivityManager activity_manager{std::move(bash)};
  profiler::proto::DaemonConfig::CpuConfig cpu_config;
  TraceManager trace_manager{
      &clock_,
      cpu_config,
      termination_service_.get(),
      &activity_manager,
      std::unique_ptr<SimpleperfManager>(new SimpleperfManager(
          std::unique_ptr<Simpleperf>(new FakeSimpleperf()))),
      std::unique_ptr<AtraceManager>(new AtraceManager(
          std::unique_ptr<FileSystem>(new MemoryFileSystem()), &clock_, 50,
          std::unique_ptr<Atrace>(new FakeAtrace(&clock_)))),
      std::unique_ptr<PerfettoManager>(
          new PerfettoManager(std::unique_ptr<Perfetto>(new FakePerfetto())))};

  // Start an ART recording.
  TraceConfiguration configuration;
  configuration.set_app_name("fake_app");
  auto art_options = configuration.mutable_art_options();
  art_options->set_trace_mode(TraceMode::SAMPLED);

  TraceStartStatus start_status;

  auto* capture = trace_manager.StartCapture(0, configuration, &start_status);

  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(start_status.status(), TraceStartStatus::SUCCESS);
  EXPECT_TRUE(MessageDifferencer::Equals(start_status, capture->start_status));
  EXPECT_THAT(cmd_1, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd_1, HasSubstr(kProfileStart));

  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
  // Now, verify that a command has been issued to stop ART recording.
  EXPECT_THAT(cmd_2, StartsWith(kAmExecutable));
  EXPECT_THAT(cmd_2, HasSubstr(kProfileStop));
}

TEST_F(TraceManagerTest, AtraceRunsOnO) { RunAtraceTest(DeviceInfo::O); }

TEST_F(TraceManagerTest, AtraceRunsOnP) { RunAtraceTest(DeviceInfo::P); }

TEST_F(TraceManagerTest, PerfettoRunsOnP) { RunPerfettoTest(DeviceInfo::P); }

TEST_F(TraceManagerTest, PerfettoRunsOnQ) { RunPerfettoTest(DeviceInfo::Q); }

TEST_F(TraceManagerTest, CannotStartMultipleTracesOnSameApp) {
  profiler::proto::DaemonConfig::CpuConfig config;
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config);

  // Start a recording.
  TraceConfiguration configuration;
  configuration.set_app_name("fake_app");
  auto simpleperf_options = configuration.mutable_simpleperf_options();

  TraceStartStatus start_status1;

  auto* capture =
      trace_manager->StartCapture(10, configuration, &start_status1);

  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(start_status1.status(), TraceStartStatus::SUCCESS);
  EXPECT_EQ(start_status1.error_code(), TraceStartStatus::NO_ERROR_TRACE_START);
  EXPECT_EQ(capture->start_timestamp, 10);
  EXPECT_EQ(capture->end_timestamp, -1);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, capture->configuration));
  EXPECT_TRUE(MessageDifferencer::Equals(start_status1, capture->start_status));

  // Starting again should fail.
  TraceStartStatus start_status2;

  capture = trace_manager->StartCapture(10, configuration, &start_status2);

  EXPECT_EQ(capture, nullptr);
  EXPECT_EQ(start_status2.status(), TraceStartStatus::FAILURE);
  EXPECT_NE(start_status2.error_code(), TraceStartStatus::NO_ERROR_TRACE_START);

  // Starting on different app is okay.
  configuration.set_app_name("fake_app2");

  TraceStartStatus start_status3;

  capture = trace_manager->StartCapture(20, configuration, &start_status3);

  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(start_status3.status(), TraceStartStatus::SUCCESS);
  EXPECT_EQ(start_status3.error_code(), TraceStartStatus::NO_ERROR_TRACE_START);
  EXPECT_EQ(capture->start_timestamp, 20);
  EXPECT_EQ(capture->end_timestamp, -1);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, capture->configuration));
  EXPECT_TRUE(MessageDifferencer::Equals(start_status3, capture->start_status));

  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
}

TEST_F(TraceManagerTest, StopBeforeStartsDoesNothing) {
  profiler::proto::DaemonConfig::CpuConfig config;
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config);

  TraceStopStatus stop_status;
  auto* capture =
      trace_manager->StopCapture(1, "fake_app", false, &stop_status);
  EXPECT_EQ(capture, nullptr);
  EXPECT_EQ(stop_status.status(), TraceStopStatus::NO_ONGOING_PROFILING);
  EXPECT_NE(stop_status.error_code(), TraceStopStatus::NO_ERROR_TRACE_STOP);

  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
}

TEST_F(TraceManagerTest, StartStopSequence) {
  profiler::proto::DaemonConfig::CpuConfig config;
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config);

  // Start a recording.
  TraceConfiguration configuration;
  configuration.set_app_name("fake_app");
  auto atrace_options = configuration.mutable_atrace_options();
  atrace_options->set_buffer_size_in_mb(8);

  TraceStartStatus start_status;

  auto* capture = trace_manager->StartCapture(10, configuration, &start_status);

  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(start_status.status(), TraceStartStatus::SUCCESS);
  EXPECT_EQ(start_status.error_code(), TraceStartStatus::NO_ERROR_TRACE_START);
  EXPECT_EQ(capture->start_timestamp, 10);
  EXPECT_EQ(capture->end_timestamp, -1);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, capture->configuration));
  EXPECT_TRUE(MessageDifferencer::Equals(start_status, capture->start_status));

  clock_.SetCurrentTime(20);
  TraceStopStatus stop_status;
  capture = trace_manager->StopCapture(15, "fake_app", false, &stop_status);
  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(stop_status.status(), TraceStopStatus::SUCCESS);
  EXPECT_EQ(stop_status.error_code(), TraceStopStatus::NO_ERROR_TRACE_STOP);
  EXPECT_EQ(capture->start_timestamp, 10);
  EXPECT_EQ(capture->end_timestamp, 20);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, capture->configuration));
  EXPECT_TRUE(MessageDifferencer::Equals(stop_status, capture->stop_status));

  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
}

TEST_F(TraceManagerTest, GetOngoingCapture) {
  profiler::proto::DaemonConfig::CpuConfig config;
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config);

  // Start a recording.
  TraceConfiguration configuration;
  configuration.set_app_name("fake_app");
  auto atrace_options = configuration.mutable_atrace_options();
  atrace_options->set_buffer_size_in_mb(8);

  TraceStartStatus start_status;

  trace_manager->StartCapture(10, configuration, &start_status);

  // Query for a different app should return null.
  auto* capture = trace_manager->GetOngoingCapture("fake_app2");
  EXPECT_EQ(capture, nullptr);

  capture = trace_manager->GetOngoingCapture("fake_app");
  EXPECT_NE(capture, nullptr);
  EXPECT_EQ(capture->start_timestamp, 10);
  EXPECT_EQ(capture->end_timestamp, -1);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, capture->configuration));
  EXPECT_TRUE(MessageDifferencer::Equals(start_status, capture->start_status));

  // Stopping the capture should return no ongoing capture.
  clock_.SetCurrentTime(20);
  TraceStopStatus stop_status;
  trace_manager->StopCapture(15, "fake_app", false, &stop_status);
  capture = trace_manager->GetOngoingCapture("fake_app");
  EXPECT_EQ(capture, nullptr);

  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
}

TEST_F(TraceManagerTest, GetCaptures) {
  profiler::proto::DaemonConfig::CpuConfig config;
  std::unique_ptr<TraceManager> trace_manager =
      ConfigureDefaultTraceManager(config);

  // Start a recording.
  TraceConfiguration configuration;
  configuration.set_app_name("fake_app1");
  auto atrace_options = configuration.mutable_atrace_options();
  atrace_options->set_buffer_size_in_mb(8);

  TraceStartStatus start_status;

  trace_manager->StartCapture(10, configuration, &start_status);

  // Query for a different app should return null.
  auto captures = trace_manager->GetCaptures("fake_app2", 0, 10);
  EXPECT_EQ(captures.size(), 0);

  // Query for out for range should return nothing.
  captures = trace_manager->GetCaptures("fake_app1", 0, 9);
  EXPECT_EQ(captures.size(), 0);

  // In-range query
  captures = trace_manager->GetCaptures("fake_app1", 0, 10);
  EXPECT_EQ(captures.size(), 1);
  EXPECT_EQ(captures[0].start_timestamp, 10);
  EXPECT_EQ(captures[0].end_timestamp, -1);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, captures[0].configuration));
  EXPECT_TRUE(
      MessageDifferencer::Equals(start_status, captures[0].start_status));

  // In-range query 2 (ongoing capture)
  captures = trace_manager->GetCaptures("fake_app1", 11, 20);
  EXPECT_EQ(captures.size(), 1);
  EXPECT_EQ(captures[0].start_timestamp, 10);
  EXPECT_EQ(captures[0].end_timestamp, -1);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, captures[0].configuration));
  EXPECT_TRUE(
      MessageDifferencer::Equals(start_status, captures[0].start_status));

  clock_.SetCurrentTime(20);
  TraceStopStatus stop_status;
  trace_manager->StopCapture(15, "fake_app1", false, &stop_status);

  // In-range query 3 (finished capture)
  captures = trace_manager->GetCaptures("fake_app1", 11, 20);
  EXPECT_EQ(captures.size(), 1);
  EXPECT_EQ(captures[0].start_timestamp, 10);
  EXPECT_EQ(captures[0].end_timestamp, 20);
  EXPECT_TRUE(
      MessageDifferencer::Equals(configuration, captures[0].configuration));
  EXPECT_TRUE(
      MessageDifferencer::Equals(start_status, captures[0].start_status));
  EXPECT_TRUE(MessageDifferencer::Equals(stop_status, captures[0].stop_status));

  // out-of-range query
  captures = trace_manager->GetCaptures("fake_app1", 21, 30);
  EXPECT_EQ(captures.size(), 0);

  // Simulate that daemon is killed.
  termination_service_.reset(nullptr);
}

}  // namespace profiler
