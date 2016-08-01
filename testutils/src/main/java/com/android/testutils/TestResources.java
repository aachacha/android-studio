/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils;

import com.google.common.io.Resources;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * Util class to help get testing resources.
 */
public final class TestResources {

    private TestResources() {}

    /**
     * Returns a file from class resources. If original resource is not file, a temp file is created
     * and returned with resource stream content; the temp file will be deleted when program exits.
     *
     * @param clazz Test class.
     * @param name Resource name.
     * @return File with resource content.
     */
    public static File getFile(Class<?> clazz, String name) {
        URL url = Resources.getResource(clazz, name);
        if (!url.getPath().contains("jar!")) {
            return new File(url.getFile());
        }

        try {
            File tempFile = File.createTempFile(name, null);
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                Resources.copy(url, outputStream);
                tempFile.deleteOnExit();
                return tempFile;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a file that is a directory from resources. If original resources is in a jar, the
     * specified directory and all files beneath are copied to system temp root directory. Those
     * copied temp files will be deleted on program exits.
     *
     * @param clazz Class with resources.
     * @param path Directory path.
     * @return Directory of given path that contains resources.
     */
    public static File getDirectory(Class<?> clazz, String path) {
        URL dirURL = Resources.getResource(clazz, path);
        switch (dirURL.getProtocol()) {
            case "file":
                return new File(dirURL.getFile());
            case "jar":
                return getDirectoryFromJar(dirURL);
            default:
                throw new UnsupportedOperationException(String.format(
                        "Unsupported protocol %s to get class %s resource directory %s",
                        dirURL.getProtocol(), clazz.getName(), path));
        }
    }

    /**
     * Returns a temp directory with the same name as given directory url. All files from that jar
     * directory are copied to the result.
     *
     * @param jarDirUrl URL of a directory in a jar.
     * @return File that is temp directory containing all files from given jar url.
     */
    private static File getDirectoryFromJar(URL jarDirUrl) {
        String dirEntryName;
        JarFile jar;
        try {
            JarURLConnection jarURLConnection = (JarURLConnection) jarDirUrl.openConnection();
            dirEntryName = jarURLConnection.getEntryName();
            jar = jarURLConnection.getJarFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Iterates all entries in jar manifest to find files under directory, then copy files.
        File root = getTempRoot();
        jar.stream().forEach(jarEntry -> {
            if (jarEntry.getName().startsWith(dirEntryName) && !jarEntry.isDirectory()) {
                File file = new File(root, jarEntry.getName());
                file.getParentFile().mkdirs();
                try (InputStream inputStream = jar.getInputStream(jarEntry)) {
                    Files.asByteSink(file).writeFrom(inputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                file.deleteOnExit();
            }
        });
        return new File(root, dirEntryName);
    }

    /**
     * Returns a directory which is the root of resources temp files.
     * @return root temp directory.
     */
    private static File getTempRoot() {
        // TODO: Find a way to get temp root without creating temp file.
        File tempFile;
        try {
            tempFile = File.createTempFile("temp", null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File root = tempFile.getParentFile();
        tempFile.delete();
        return root;
    }
}
