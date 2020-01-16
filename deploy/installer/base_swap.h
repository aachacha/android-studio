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

#ifndef BASE_SWAP_H
#define BASE_SWAP_H

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/server/install_client.h"
#include "tools/base/deploy/proto/deploy.pb.h"

#include <string>
#include <unordered_set>
#include <vector>

namespace deploy {

class BaseSwapCommand : public Command {
 public:
  BaseSwapCommand(Workspace& workspace) : Command(workspace) {}
  virtual ~BaseSwapCommand() = default;
  virtual void Run(proto::InstallerResponse* response) final;

 protected:
  std::unique_ptr<InstallClient> client_;

  // Swap parameters
  std::string package_name_;
  std::vector<int> process_ids_;
  int extra_agents_count_;

  // Derived classes should override this to set up for the swap, including
  // copying the agent binary to the appropriate location and building the swap
  // request.
  virtual proto::SwapRequest PrepareAndBuildRequest(
      proto::SwapResponse* response) = 0;

  // Derived classes should override this to handle the SwapResponse returned
  // from the Swap() method, which aggregates all the AgentSwapResponses into a
  // single message.
  virtual void ProcessResponse(proto::SwapResponse* response) = 0;

  // This must be called by derived classes in the ParseParameters method to set
  // up for the swap.
  void SetSwapParameters(const std::string& package_name,
                         const std::vector<int>& process_ids,
                         int extra_agents_count) {
    package_name_ = package_name;
    process_ids_ = process_ids;
    extra_agents_count_ = extra_agents_count;
  }

  // This must be called by derived classes in the PrepareAndBuildRequest method
  // with the paths to the agent and agent server to be used for swapping.
  void SetAgentPaths(const std::string& agent_path,
                     const std::string& agent_server_path) {
    agent_path_ = agent_path;
    agent_server_path_ = agent_server_path;
  }

  // Sends a request to the server to check for the existence of files
  // accessible to the target package.
  bool CheckFilesExist(const std::vector<std::string>& files,
                       std::unordered_set<std::string>* missing_files);

  const std::string kAgent = "agent.so";
  const std::string kAgentAlt = "agent-alt.so";
  const std::string kAgentServer = "agent_server";
  const std::string kInstallServer = "install_server";

 private:
  std::string agent_path_;
  std::string agent_server_path_;

  void Swap(const proto::SwapRequest& request, proto::SwapResponse* response);

  bool ExtractBinaries(const std::string& target_dir,
                       const std::vector<std::string>& files_to_extract) const;

  bool WriteArrayToDisk(const unsigned char* array, uint64_t array_len,
                        const std::string& dst_path) const noexcept;

  bool StartAgentServer(int agent_count, int* server_pid, int* read_fd,
                        int* write_fd) const;

  bool AttachAgents() const;
};

}  // namespace deploy

#endif  // DEPLOYER_BASE_INSTALL