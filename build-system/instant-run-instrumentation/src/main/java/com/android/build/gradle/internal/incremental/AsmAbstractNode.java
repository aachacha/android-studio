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

package com.android.build.gradle.internal.incremental;

import com.android.annotations.NonNull;
import org.objectweb.asm.tree.ClassNode;

/** Encapsulation of an Asm {@link ClassNode} reference. */
class AsmAbstractNode {
    @NonNull private final ClassNode classNode;

    protected AsmAbstractNode(@NonNull ClassNode classNode) {
        this.classNode = classNode;
    }

    @NonNull
    public ClassNode getClassNode() {
        return classNode;
    }
}
