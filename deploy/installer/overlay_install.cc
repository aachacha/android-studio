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

#include "tools/base/deploy/installer/overlay_install.h"

#include <string>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/executor/executor.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/installer/server/install_server.h"

namespace {
const std::string kAgent = "agent.so";
const std::string kAgentAlt = "agent-alt.so";
const std::string kInstallServer = "install_server";
}  // namespace

namespace deploy {

void OverlayInstallCommand::ParseParameters(
    const proto::InstallerRequest& request) {
  if (!request.has_overlay_install()) {
    return;
  }
  request_ = request.overlay_install();

  ready_to_run_ = true;
}

void OverlayInstallCommand::Run(proto::InstallerResponse* response) {
  proto::OverlayInstallResponse* overlay_response =
      response->mutable_overlay_install_response();

  // Determine which agent we need to use.
#if defined(__aarch64__) || defined(__x86_64__)
  std::string agent =
      request_.arch() == proto::Arch::ARCH_64_BIT ? kAgent : kAgentAlt;
#else
  std::string agent = kAgent;
#endif

  if (!ExtractBinaries(workspace_.GetTmpFolder(), {agent, kInstallServer})) {
    overlay_response->set_status(proto::OverlayInstallResponse::SETUP_FAILED);
    ErrEvent("Extracting binaries failed");
    return;
  }

  const std::string server_name =
      kInstallServer + "-" + workspace_.GetVersion();
  client_ = StartInstallServer(Executor::Get(),
                               workspace_.GetTmpFolder() + kInstallServer,
                               request_.package_name(), server_name);

  if (!client_) {
    overlay_response->set_status(
        proto::OverlayInstallResponse::START_SERVER_FAILED);
    return;
  }

  SetUpAgent(agent, overlay_response);
  UpdateOverlay(overlay_response);
  GetAgentLogs(overlay_response);

  proto::InstallServerResponse install_response;
  if (!client_->KillServerAndWait(&install_response)) {
    overlay_response->set_status(
        proto::OverlayInstallResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  for (int i = 0; i < install_response.events_size(); i++) {
    const proto::Event& event = install_response.events(i);
    AddRawEvent(ConvertProtoEventToEvent(event));
  }
}

void OverlayInstallCommand::SetUpAgent(
    const std::string& agent, proto::OverlayInstallResponse* overlay_response) {
  Phase p("SetUpAgent");

  std::string version = workspace_.GetVersion() + "-";
  std::string code_cache =
      "/data/data/" + request_.package_name() + "/code_cache/";

  std::string startup_path = code_cache + "startup_agents/";
  std::string studio_path = code_cache + ".studio/";
  std::string agent_path = startup_path + version + agent;

  std::unordered_set<std::string> missing_files;
  CheckFilesExist({startup_path, studio_path, agent_path}, &missing_files);

  RunasExecutor run_as(request_.package_name());
  std::string error;

  bool missing_startup =
      missing_files.find(startup_path) != missing_files.end();
  bool missing_agent = missing_files.find(agent_path) != missing_files.end();

  // Clean up other agents from the startup_agent directory. Because agents are
  // versioned (agent-<version#>) we cannot simply copy our agent on top of the
  // previous file. If the startup_agent directory exists but our agent cannot
  // be found in it, we assume another agent is present and delete it.
  if (!missing_startup && missing_agent) {
    if (!run_as.Run("rm", {"-f", "-r", startup_path}, nullptr, &error)) {
      ErrEvent("Could not remove old agents: " + error);
      overlay_response->set_status(proto::OverlayInstallResponse::SETUP_FAILED);
      return;
    }
    missing_startup = true;
  }

  if (missing_startup &&
      !run_as.Run("mkdir", {startup_path}, nullptr, &error)) {
    ErrEvent("Could not create startup agent directory: " + error);
    overlay_response->set_status(proto::OverlayInstallResponse::SETUP_FAILED);
    return;
  }

  if (missing_files.find(studio_path) != missing_files.end() &&
      !run_as.Run("mkdir", {studio_path}, nullptr, &error)) {
    ErrEvent("Could not create studio directory: " + error);
    overlay_response->set_status(proto::OverlayInstallResponse::SETUP_FAILED);
    return;
  }

  std::string tmp_agent = workspace_.GetTmpFolder() + agent;
  if (missing_agent &&
      !run_as.Run("cp", {"-F", tmp_agent, agent_path}, nullptr, &error)) {
    ErrEvent("Could not copy binaries: " + error);
    overlay_response->set_status(proto::OverlayInstallResponse::SETUP_FAILED);
    return;
  }
}

void OverlayInstallCommand::UpdateOverlay(
    proto::OverlayInstallResponse* overlay_response) {
  Phase p("UpdateOverlay");

  proto::InstallServerRequest install_request;
  install_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);

  auto overlay_request = install_request.mutable_overlay_request();
  overlay_request->set_overlay_id(request_.overlay_id());
  overlay_request->set_expected_overlay_id(request_.expected_overlay_id());

  const std::string overlay_path =
      "/data/data/" + request_.package_name() + "/code_cache";
  overlay_request->set_overlay_path(overlay_path);

  for (auto overlay_file : request_.overlay_files()) {
    auto file = overlay_request->add_files_to_write();
    file->set_path(overlay_file.path());
    file->set_allocated_content(overlay_file.release_content());
  }

  for (auto deleted_file : request_.deleted_files()) {
    overlay_request->add_files_to_delete(deleted_file);
  }

  if (!client_->Write(install_request)) {
    ErrEvent("Could not write overlay update to install server");
    overlay_response->set_status(
        proto::OverlayInstallResponse::WRITE_TO_SERVER_FAILED);
    return;
  }

  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    ErrEvent("Could not read response from install server");
    overlay_response->set_status(
        proto::OverlayInstallResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  switch (install_response.overlay_response().status()) {
    case proto::OverlayUpdateResponse::OK:
      overlay_response->set_status(proto::OverlayInstallResponse::OK);
      return;
    case proto::OverlayUpdateResponse::ID_MISMATCH:
      overlay_response->set_status(
          proto::OverlayInstallResponse::OVERLAY_ID_MISMATCH);
      overlay_response->set_extra(
          install_response.overlay_response().error_message());
      return;
    case proto::OverlayUpdateResponse::UPDATE_FAILED:
      overlay_response->set_status(
          proto::OverlayInstallResponse::OVERLAY_UPDATE_FAILED);
      overlay_response->set_extra(
          install_response.overlay_response().error_message());
      return;
  }
}

bool OverlayInstallCommand::CheckFilesExist(
    const std::vector<std::string>& files,
    std::unordered_set<std::string>* missing_files) {
  Phase p("CheckFilesExist");

  proto::InstallServerRequest request;
  request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  for (const std::string& file : files) {
    request.mutable_check_request()->add_files(file);
  }
  proto::InstallServerResponse response;
  if (!client_->Write(request) || !client_->Read(&response)) {
    return false;
  }

  missing_files->insert(response.check_response().missing_files().begin(),
                        response.check_response().missing_files().end());
  return true;
}

void OverlayInstallCommand::GetAgentLogs(
    proto::OverlayInstallResponse* response) {
  Phase p("GetAgentLogs");
  proto::InstallServerRequest install_request;
  install_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);
  install_request.mutable_log_request()->set_package_name(
      request_.package_name());

  // If this fails, we don't really care - it's a best-effort situation; don't
  // break the deployment because of it. Just log and move on.
  if (!client_->Write(install_request)) {
    Log::W("Could not write to server to retrieve agent logs.");
    return;
  }

  proto::InstallServerResponse install_response;
  if (!client_->Read(&install_response)) {
    Log::W("Could not read from server while retrieving agent logs.");
    return;
  }

  for (const auto& log : install_response.log_response().logs()) {
    auto added = response->add_agent_logs();
    *added = log;
  }
}

}  // namespace deploy
