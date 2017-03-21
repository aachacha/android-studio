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

#include "debuginfo_encoder.h"
#include "chronometer.h"
#include "common.h"

#include <assert.h>

namespace lir {

bool DebugInfoEncoder::Visit(DbgInfoHeader* dbg_header) {
  assert(param_names_ == nullptr);
  param_names_ = &dbg_header->param_names;
  return true;
}

bool DebugInfoEncoder::Visit(DbgInfoAnnotation* dbg_annotation) {
  // keep the address in sync
  if (last_address_ != dbg_annotation->offset) {
    CHECK(dbg_annotation->offset > last_address_);
    dbginfo_.Push<dex::u1>(dex::DBG_ADVANCE_PC);
    dbginfo_.PushULeb128(dbg_annotation->offset - last_address_);
    last_address_ = dbg_annotation->offset;
  }

  // encode the annotation itself
  switch (dbg_annotation->dbg_opcode) {
    case dex::DBG_ADVANCE_LINE: {
      int line = dbg_annotation->CastOperand<LineNumber>(0)->line;
      if (line_start_ == 0) {
        // it's not perfectly clear from the .dex specification
        // if initial line == 0 is valid, but a number of existing
        // .dex files do this so we have to support it
        CHECK(line >= 0);
        line_start_ = line;
      } else {
        WEAK_CHECK(line > 0);
        dbginfo_.Push<dex::u1>(dex::DBG_ADVANCE_LINE);
        dbginfo_.PushSLeb128(line - last_line_);
      }
      last_line_ = line;
    } break;

    case dex::DBG_START_LOCAL: {
      auto reg = dbg_annotation->CastOperand<VReg>(0)->reg;
      auto name_index = dbg_annotation->CastOperand<String>(1)->index;
      auto type_index = dbg_annotation->CastOperand<Type>(2)->index;
      dbginfo_.Push<dex::u1>(dex::DBG_START_LOCAL);
      dbginfo_.PushULeb128(reg);
      dbginfo_.PushULeb128(name_index + 1);
      dbginfo_.PushULeb128(type_index + 1);
    } break;

    case dex::DBG_START_LOCAL_EXTENDED: {
      auto reg = dbg_annotation->CastOperand<VReg>(0)->reg;
      auto name_index = dbg_annotation->CastOperand<String>(1)->index;
      auto type_index = dbg_annotation->CastOperand<Type>(2)->index;
      auto sig_index = dbg_annotation->CastOperand<String>(3)->index;
      dbginfo_.Push<dex::u1>(dex::DBG_START_LOCAL_EXTENDED);
      dbginfo_.PushULeb128(reg);
      dbginfo_.PushULeb128(name_index + 1);
      dbginfo_.PushULeb128(type_index + 1);
      dbginfo_.PushULeb128(sig_index + 1);
    } break;

    case dex::DBG_END_LOCAL:
    case dex::DBG_RESTART_LOCAL: {
      auto reg = dbg_annotation->CastOperand<VReg>(0)->reg;
      dbginfo_.Push<dex::u1>(dbg_annotation->dbg_opcode);
      dbginfo_.PushULeb128(reg);
    } break;

    case dex::DBG_SET_PROLOGUE_END:
    case dex::DBG_SET_EPILOGUE_BEGIN:
      dbginfo_.Push<dex::u1>(dbg_annotation->dbg_opcode);
      break;

    case dex::DBG_SET_FILE: {
      auto file_name = dbg_annotation->CastOperand<String>(0);
      if (file_name->ir_string != source_file_) {
        source_file_ = file_name->ir_string;
        dbginfo_.Push<dex::u1>(dex::DBG_SET_FILE);
        dbginfo_.PushULeb128(file_name->index + 1);
      }
    } break;

    default:
      FATAL("Unexpected debug info opcode: 0x%02x", dbg_annotation->dbg_opcode);
  }

  return true;
}

void DebugInfoEncoder::Encode(ir::EncodedMethod* ir_method, std::shared_ptr<ir::DexFile> dex_ir) {
  auto ir_debug_info = ir_method->code->debug_info;

  CHECK(dbginfo_.empty());
  CHECK(param_names_ == nullptr);
  CHECK(line_start_ == 0);
  CHECK(last_line_ == 0);
  CHECK(last_address_ == 0);
  CHECK(source_file_ == nullptr);

  // generate new debug info
  source_file_ = ir_method->parent_class->source_file;
  for (auto instr : instructions_) {
    instr->Accept(this);
  }
  dbginfo_.Push<dex::u1>(dex::DBG_END_SEQUENCE);
  dbginfo_.Seal(1);

  CHECK(!dbginfo_.empty());

  // update ir::DebugInfo
  ir_debug_info->line_start = line_start_;
  ir_debug_info->data = slicer::MemView(dbginfo_.data(), dbginfo_.size());

  if (param_names_ != nullptr) {
    ir_debug_info->param_names = *param_names_;
  } else {
    ir_debug_info->param_names = {};
  }

  // attach the debug info buffer to the dex IR
  dex_ir->AttachBuffer(std::move(dbginfo_));
}

}  // namespace lir
