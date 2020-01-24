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

package test.inspector.api;

import androidx.annotation.NonNull;

public final class TodoInspectorApi {
    public enum Command {
        UNKNOWN,

        COUNT_TODO_GROUPS,
        COUNT_TODO_ITEMS;

        @NonNull
        public byte[] toByteArray() {
            return new byte[] {(byte) ordinal()};
        }
    }

    public enum Event {
        UNKNOWN,

        TODO_GROUP_CREATED,
        TODO_ITEM_CREATED;

        @NonNull
        public byte[] toByteArray() {
            return new byte[] {(byte) ordinal()};
        }
    }
}
