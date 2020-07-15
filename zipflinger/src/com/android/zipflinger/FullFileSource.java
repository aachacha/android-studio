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

package com.android.zipflinger;

import com.android.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.Deflater;

public class FullFileSource extends BytesSource {

    public enum Symlink {
        FOLLOW,
        DO_NOT_FOLLOW
    };

    public FullFileSource(@NonNull String filePath, @NonNull String entryName, int compressionLevel)
            throws IOException {
        this(filePath, entryName, compressionLevel, Symlink.FOLLOW);
    }

    public FullFileSource(
            @NonNull String filePath,
            @NonNull String entryName,
            int compressionLevel,
            Symlink symlinkPolicy)
            throws IOException {
        super(entryName);

        Path path = Paths.get(filePath);

        if (Files.isExecutable(path)) {
            externalAttributes |= PERMISSION_EXEC;
        }

        byte[] bytes;
        if (!Files.isSymbolicLink(path) || symlinkPolicy == Symlink.FOLLOW) {
            bytes = Files.readAllBytes(path);
        } else {
            externalAttributes |= PERMISSION_LINK;
            compressionLevel = Deflater.NO_COMPRESSION;
            Path target = Files.readSymbolicLink(path);
            bytes = target.toString().getBytes(StandardCharsets.US_ASCII);
        }

        build(bytes, bytes.length, compressionLevel);
    }
}
