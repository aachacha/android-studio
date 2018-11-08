/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.tools.deployer.model.Apk;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PatchGenerator {

    public static class Patch {
        final ByteBuffer data;
        final ByteBuffer instructions;
        final String sourcePath; // Path to apk used as source of clean data on the device.
        final long destinationSize; // Size of apk to generate on the device.

        Patch(ByteBuffer data, ByteBuffer instructions, String sourcePath, long destinationSize) {
            this.data = data;
            this.instructions = instructions;
            this.sourcePath = sourcePath;
            this.destinationSize = destinationSize;
        }
    }

    /**
     * Generate the instructions and payload necessary to generate the localApk using a patch and
     * the remoteApk only.
     *
     * <p>Patch are generated by leveraging the Central Directory of an.apk files. By using only the
     * CD of each apks on the device (typically accounting for a few KiB), and then comparing CD
     * entries in the local apk on the host, a map of dirty areas is generated.
     *
     * @return A Patch to apply to a file in order to turn the remoteApk into the localApk.
     */
    public Patch generate(Apk remoteApk, Apk localApk) throws IOException {
        String sourcePath = remoteApk.path;
        long destinationSize = Files.size(Paths.get(localApk.path));

        // Generate maps from each apk, based on the content directory.
        List<ApkMap.Area> dirtyAreas = generateDirtyMap(remoteApk, localApk);

        // Use the map of what is dirty and what is clean in the archive to build the patching instruction.
        int patchSize = 0;
        for (ApkMap.Area dirtyArea : dirtyAreas) {
            patchSize += dirtyArea.size();
        }
        ByteBuffer data = ByteBuffer.wrap(new byte[patchSize]);
        ByteBuffer instructions =
                ByteBuffer.wrap(new byte[dirtyAreas.size() * 8]).order(ByteOrder.LITTLE_ENDIAN);

        Trace.begin("building patch");
        try (FileChannel fileChannel =
                new RandomAccessFile(new File(localApk.path), "r").getChannel()) {
            for (ApkMap.Area dirtyArea : dirtyAreas) {
                instructions.putInt((int) dirtyArea.start);
                instructions.putInt((int) dirtyArea.size());
                data.limit((int) (data.position() + dirtyArea.size()));
                fileChannel.read(data, dirtyArea.start);
            }
        }
        Trace.end();

        data.rewind();
        instructions.rewind();
        return new Patch(data, instructions, sourcePath, destinationSize);
    }

    private List<ApkMap.Area> generateDirtyMap(Apk remoteApk, Apk localApk) throws IOException {
        Trace.begin("marking dirty");
        HashMap<String, ZipUtils.ZipEntry> remoteApkEntries = remoteApk.zipEntries;
        HashMap<String, ZipUtils.ZipEntry> localApkEntries = localApk.zipEntries;
        ApkMap dirtyMap = new ApkMap(Files.size(Paths.get(localApk.path)));

        for (ZipUtils.ZipEntry remoteEntry : remoteApkEntries.values()) {
            ZipUtils.ZipEntry localEntry = localApkEntries.get(remoteEntry.name);
            if (localEntry == null) {
                continue; // Skip Deleted file
            }
            if (!Arrays.equals(remoteEntry.localFileHeader, localEntry.localFileHeader)) {
                // This entry has changed and is considered dirty.
                continue;
            }

            // The entry has not changed. We can use it to declare either one or two Clean areas.
            // Since the "extra" field is not covered by the CRC, we cannot assume anything about
            // the content. We must mark it as dirty if it has a size above zero.
            if (localEntry.extraLength == 0) {
                ApkMap.Area cleanArea = new ApkMap.Area(localEntry.start, localEntry.end);
                dirtyMap.markClean(cleanArea);
            } else {
                // We don't know what is inside the extra field so we need to leave it marked as
                // dirty.
                ApkMap.Area headerUpToName =
                        new ApkMap.Area(
                                localEntry.start,
                                localEntry.payloadStart - localEntry.extraLength - 1);
                dirtyMap.markClean(headerUpToName);

                ApkMap.Area compresseDataArea =
                        new ApkMap.Area(localEntry.payloadStart, localEntry.end);
                dirtyMap.markClean(compresseDataArea);
            }
        }
        Trace.end();
        return dirtyMap.getDirtyAreas();
    }
}
