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

#ifndef OVERLAY_SWAP_H
#define OVERLAY_SWAP_H

#include "tools/base/deploy/installer/base_swap.h"
#include "tools/base/deploy/proto/deploy.pb.h"

#include <string>
#include <unordered_set>
#include <vector>

namespace deploy {

// This class interacts with the install server to perform a swap with overlay
// update. It performs the following steps:
// - Send a SetupCheckRequest to the install server to see if any files (agent,
// agent_server, etc.) need to be copied
// - Start the agent server and send a SwapRequest
// - Send an OverlayUpdateRequest to the install server to update the overlays
class OverlaySwapCommand : public BaseSwapCommand {
 public:
  OverlaySwapCommand(Workspace& workspace) : BaseSwapCommand(workspace) {}
  virtual ~OverlaySwapCommand() = default;
  virtual void ParseParameters(int argc, char** argv);

 protected:
  virtual proto::SwapRequest PrepareAndBuildRequest(
      proto::SwapResponse* response) override;
  virtual void ProcessResponse(proto::SwapResponse* response) override;

 private:
  proto::OverlaySwapRequest request_;

  // Helper method to convert a repeated field of OverlayFile into DexClass
  // protos and thus create a SwapRequest from an OverlayUpdateRequest.
  void PopulateClasses(
      google::protobuf::RepeatedPtrField<proto::OverlayFile> overlay_files,
      proto::SwapRequest* swap_request);
};

}  // namespace deploy

#endif  // DEPLOYER_BASE_INSTALL