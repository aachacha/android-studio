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

package com.android.build.gradle.internal.tasks.mlkit.codegen;

import com.android.tools.mlkit.MetadataExtractor;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class ModelUtils {

    public static MetadataExtractor createMetadataExtractor(File modelFile) {
        try {
            try (RandomAccessFile f = new RandomAccessFile(modelFile, "r")) {
                byte[] data = new byte[(int) f.length()];
                f.readFully(data);
                ByteBuffer bb = ByteBuffer.wrap(data);
                return new MetadataExtractor(bb);
            }

        } catch (IOException e) {
            return null;
        }
    }
}
