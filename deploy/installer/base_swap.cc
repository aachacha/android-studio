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

#include "tools/base/deploy/installer/base_swap.h"

#include <fcntl.h>
#include <sys/wait.h>

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/binary_extract.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor/runas_executor.h"
#include "tools/base/deploy/installer/server/install_server.h"

namespace {
// These values are based on FIRST_APPLICATION_UID and LAST_APPLICATION_UID in
// android.os.Process, which we assume are stable since they haven't been
// changed since 2012.
const int kFirstAppUid = 10000;
const int kLastAppUid = 19999;

bool isUserDebug() {
  return deploy::Env::build_type().find("userdebug") != std::string::npos;
}

}  // namespace

namespace deploy {

void BaseSwapCommand::Run(proto::InstallerResponse* response) {
  proto::SwapResponse* swap_response = response->mutable_swap_response();

  if (!ExtractBinaries(workspace_.GetTmpFolder(),
                       {kAgent, kAgentAlt, kInstallServer})) {
    swap_response->set_status(proto::SwapResponse::SETUP_FAILED);
    ErrEvent("Extracting binaries failed");
    return;
  }

  client_ = StartInstallServer(
      workspace_.GetExecutor(), workspace_.GetTmpFolder() + kInstallServer,
      package_name_, kInstallServer + "-" + workspace_.GetVersion());

  if (!client_) {
    if (isUserDebug()) {
      swap_response->set_status(
          proto::SwapResponse::START_SERVER_FAILED_USERDEBUG);
    } else {
      swap_response->set_status(proto::SwapResponse::START_SERVER_FAILED);
    }
    swap_response->set_extra(kInstallServer);
    return;
  }

  proto::SwapRequest request = PrepareAndBuildRequest(swap_response);
  Swap(request, swap_response);
  ProcessResponse(swap_response);
}

void BaseSwapCommand::Swap(const proto::SwapRequest& swap_request,
                           proto::SwapResponse* swap_response) {
  Phase p("Swap");
  if (swap_response->status() != proto::SwapResponse::UNKNOWN) {
    return;
  }

  // Remove process ids that we do not need to swap.
  FilterProcessIds(&process_ids_);

  // Don't bother with the server if we have no work to do.
  if (process_ids_.empty() && extra_agents_count_ == 0) {
    LogEvent("No PIDs needs to be swapped");
    swap_response->set_status(proto::SwapResponse::OK);
    return;
  }

  // Request for the install-server to open a socket and begin listening for
  // agents to connect. Agents connect shortly after they are attached (below).
  proto::SwapResponse::Status status = ListenForAgents();
  if (status != proto::SwapResponse::OK) {
    swap_response->set_status(status);
    return;
  }

  if (!AttachAgents()) {
    swap_response->set_status(proto::SwapResponse::AGENT_ATTACH_FAILED);
    return;
  }

  // Request for the install-server to accept a connection for each agent
  // attached. The install-server will forward the specified swap request to
  // every agent, then return an aggregate list of each agent's response.
  proto::InstallServerRequest server_request;
  server_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);

  auto send_request = server_request.mutable_send_request();
  send_request->set_agent_count(process_ids_.size() + extra_agents_count_);
  *send_request->mutable_swap_request() = swap_request;

  if (!client_->Write(server_request)) {
    swap_response->set_status(proto::SwapResponse::WRITE_TO_SERVER_FAILED);
    return;
  }

  proto::InstallServerResponse server_response;
  if (!client_->Read(&server_response) ||
      server_response.status() !=
          proto::InstallServerResponse::REQUEST_COMPLETED) {
    swap_response->set_status(proto::SwapResponse::READ_FROM_SERVER_FAILED);
    return;
  }

  const auto& agent_server_response = server_response.send_response();
  for (const auto& agent_response : agent_server_response.agent_responses()) {
    // Convert proto events to events.
    for (int i = 0; i < agent_response.events_size(); i++) {
      const proto::Event& event = agent_response.events(i);
      AddRawEvent(ConvertProtoEventToEvent(event));
    }

    if (agent_response.status() != proto::AgentSwapResponse::OK) {
      auto failed_agent = swap_response->add_failed_agents();
      *failed_agent = agent_response;
    }
  }

  if (agent_server_response.status() == proto::SendAgentMessageResponse::OK) {
    if (swap_response->failed_agents_size() == 0) {
      swap_response->set_status(proto::SwapResponse::OK);
    } else {
      swap_response->set_status(proto::SwapResponse::AGENT_ERROR);
    }
    return;
  }

  CmdCommand cmd(workspace_);
  std::vector<ProcessRecord> records;
  if (cmd.GetProcessInfo(package_name_, &records)) {
    for (auto& record : records) {
      if (record.crashing) {
        swap_response->set_status(proto::SwapResponse::PROCESS_CRASHING);
        swap_response->set_extra(record.process_name);
        return;
      }

      if (record.not_responding) {
        swap_response->set_status(proto::SwapResponse::PROCESS_NOT_RESPONDING);
        swap_response->set_extra(record.process_name);
        return;
      }
    }
  }

  for (int pid : swap_request.process_ids()) {
    const std::string pid_string = to_string(pid);
    if (IO::access("/proc/" + pid_string, F_OK) != 0) {
      swap_response->set_status(proto::SwapResponse::PROCESS_TERMINATED);
      swap_response->set_extra(pid_string);
      return;
    }
  }

  swap_response->set_status(proto::SwapResponse::MISSING_AGENT_RESPONSES);
}

void BaseSwapCommand::FilterProcessIds(std::vector<int>* process_ids) {
  Phase p("FilterProcessIds");
  auto it = process_ids->begin();
  while (it != process_ids->end()) {
    const int pid = *it;
    const std::string pid_path = "/proc/" + to_string(pid);
    struct stat proc_dir_stat;
    if (IO::stat(pid_path, &proc_dir_stat) < 0) {
      LogEvent("Ignoring pid '" + to_string(pid) + "'; could not stat().");
      it = process_ids->erase(it);
    } else if (proc_dir_stat.st_uid < kFirstAppUid ||
               proc_dir_stat.st_uid > kLastAppUid) {
      LogEvent("Ignoring pid '" + to_string(pid) +
               "'; uid=" + to_string(proc_dir_stat.st_uid) +
               " is not in the app uid range.");
      it = process_ids->erase(it);
    } else {
      ++it;
    }
  }
}

proto::SwapResponse::Status BaseSwapCommand::ListenForAgents() const {
  Phase("ListenForAgents");
  proto::InstallServerRequest server_request;
  server_request.set_type(proto::InstallServerRequest::HANDLE_REQUEST);

  auto socket_request = server_request.mutable_socket_request();
  socket_request->set_socket_name(Socket::kDefaultAddress);

  if (!client_->Write(server_request)) {
    return proto::SwapResponse::WRITE_TO_SERVER_FAILED;
  }

  proto::InstallServerResponse server_response;
  if (!client_->Read(&server_response)) {
    return proto::SwapResponse::READ_FROM_SERVER_FAILED;
  }

  if (server_response.status() !=
          proto::InstallServerResponse::REQUEST_COMPLETED ||
      server_response.socket_response().status() !=
          proto::OpenAgentSocketResponse::OK) {
    return proto::SwapResponse::READY_FOR_AGENTS_NOT_RECEIVED;
  }

  return proto::SwapResponse::OK;
}

bool BaseSwapCommand::AttachAgents() const {
  Phase p("AttachAgents");
  CmdCommand cmd(workspace_);
  for (int pid : process_ids_) {
    std::string output;
    LogEvent("Attaching agent: '"_s + agent_path_ + "'");
    output = "";
    if (!cmd.AttachAgent(pid, agent_path_, {Socket::kDefaultAddress},
                         &output)) {
      ErrEvent("Could not attach agent to process: "_s + output);
      return false;
    }
  }
  return true;
}

bool BaseSwapCommand::CheckFilesExist(
    const std::vector<std::string>& files,
    std::unordered_set<std::string>* missing_files) {
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

}  // namespace deploy
