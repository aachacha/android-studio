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
package com.android.repository.io.impl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.io.CancellableFileIo;
import com.android.repository.io.FileOp;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Base class for {@link FileOp} implementations based on a {@link java.nio.file.Files}.
 * Uses {@link CancellableFileIo} to check for cancellation before read I/O operations.
 */
public abstract class FileSystemFileOp implements FileOp {
    protected boolean mIsWindows;

    public FileSystemFileOp() {
        mIsWindows = System.getProperty("os.name").startsWith("Windows");
    }

    /** Returns the {@link FileSystem} this is based on. */
    public abstract FileSystem getFileSystem();

    @Override
    public final boolean isWindows() {
        return mIsWindows;
    }

    @NonNull
    @Override
    public final String readText(@NonNull File f) throws IOException {
        return CancellableFileIo.readText(toPath(f));
    }

    @Override
    public final String[] list(@NonNull File folder, @Nullable FilenameFilter filenameFilter) {
        File[] contents = listFiles(folder);
        String[] names = new String[contents.length];
        for (int i = 0; i < contents.length; i++) {
            names[i] = contents[i].getName();
        }
        if (filenameFilter == null) {
            return names;
        }
        List<String> result = new ArrayList<>();
        for (String name : names) {
            if (filenameFilter.accept(folder, name)) {
                result.add(name);
            }
        }
        return result.toArray(new String[0]);

    }

    @Override
    public final File[] listFiles(@NonNull File folder, @Nullable FilenameFilter filenameFilter) {
        File[] contents = listFiles(folder);
        if (filenameFilter == null) {
            return contents;
        }
        List<File> result = new ArrayList<>();
        for (File f : contents) {
            if (filenameFilter.accept(folder, f.getName())) {
                result.add(f);
            }
        }
        return result.toArray(new File[0]);

    }

    @Override
    public final boolean setLastModified(@NonNull File file, long time) throws IOException {
        return setLastModified(toPath(file), time);
    }

    @Override
    public final boolean setLastModified(@NonNull Path file, long time) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(time));
        return true;
    }

    @Override
    public final void deleteFileOrFolder(@NonNull File fileOrFolder) {
        if (isDirectory(fileOrFolder)) {
            // Must delete content recursively first
            for (File item : listFiles(fileOrFolder)) {
                deleteFileOrFolder(item);
            }
        }
        delete(fileOrFolder);
    }

    @Override
    public boolean deleteFileOrFolder(@NonNull Path fileOrFolder) {
        boolean[] sawException = new boolean[1];
        try (Stream<Path> contents = CancellableFileIo.walk(fileOrFolder)) {
            contents.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    sawException[0] = true;
                                }
                            });
        } catch (IOException e) {
            return false;
        }
        return !sawException[0];
    }

    @Override
    public final void setExecutablePermission(@NonNull File file) throws IOException {
        Path path = toPath(file);
        Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(path));
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        permissions.add(PosixFilePermission.GROUP_EXECUTE);
        permissions.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(path, permissions);
    }

    @Override
    public final boolean canExecute(@NonNull File file) {
        return CancellableFileIo.isExecutable(toPath(file));
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @Override
    public void setReadOnly(@NonNull File file) throws IOException {
        Path path = toPath(file);
        Set<PosixFilePermission> permissions = EnumSet.copyOf(Files.getPosixFilePermissions(path));
        permissions.remove(PosixFilePermission.OWNER_WRITE);
        permissions.remove(PosixFilePermission.GROUP_WRITE);
        permissions.remove(PosixFilePermission.OTHERS_WRITE);
        Files.setPosixFilePermissions(path, permissions);
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @Override
    public void copyFile(@NonNull File source, @NonNull File dest) throws IOException {
        Files.copy(toPath(source), toPath(dest));
    }

    @Override
    public final boolean isSameFile(@NonNull File file1, @NonNull File file2) throws IOException {
        return CancellableFileIo.isSameFile(toPath(file1), toPath(file2));
    }

    @Override
    public final boolean isFile(@NonNull File file) {
        return CancellableFileIo.isRegularFile(toPath(file));
    }

    @Override
    public final boolean isDirectory(@NonNull File file) {
        return CancellableFileIo.isDirectory(toPath(file));
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @Override
    public boolean canWrite(@NonNull File file) {
        return CancellableFileIo.isWritable(toPath(file));
    }

    @Override
    public final boolean exists(@NonNull File file) {
        return CancellableFileIo.exists(toPath(file));
    }

    @Override
    public final long length(@NonNull File file) throws IOException {
        return CancellableFileIo.size(toPath(file));
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @Override
    public boolean delete(@NonNull File file) {
        try {
            Files.delete(toPath(file));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public final boolean mkdirs(@NonNull File file) {
        try {
            Files.createDirectories(toPath(file));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    @Override
    public final File[] listFiles(@NonNull File file) {
        try (Stream<Path> children = CancellableFileIo.list(toPath(file))) {
            return children.map(path -> new File(path.toString())).toArray(File[]::new);
        }
        catch (IOException e) {
            return new File[0];
        }
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @Override
    public boolean renameTo(@NonNull File oldFile, @NonNull File newFile) {
        try {
            Files.move(toPath(oldFile), toPath(newFile));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @NonNull
    @Override
    public OutputStream newFileOutputStream(@NonNull File file) throws IOException {
        return newFileOutputStream(file, false);
    }

    // TODO: make this final so we can migrate from FileOp to using Paths directly
    @NonNull
    @Override
    public OutputStream newFileOutputStream(@NonNull File file, boolean append)
            throws IOException {
        if (append) {
            return Files.newOutputStream(toPath(file), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(toPath(file), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    @NonNull
    @Override
    public final InputStream newFileInputStream(@NonNull File file) throws IOException {
        return newFileInputStream(toPath(file));
    }

    @NonNull
    @Override
    public final InputStream newFileInputStream(@NonNull Path path) throws IOException {
        return CancellableFileIo.newInputStream(path);
    }

    @Override
    public final long lastModified(@NonNull File file) {
        try {
            return CancellableFileIo.getLastModifiedTime(toPath(file)).toMillis();
        }
        catch (IOException e) {
            return 0;
        }
    }

    @Override
    public final boolean createNewFile(@NonNull File file) throws IOException {
        try {
            Path path = toPath(file);
            Files.createDirectories(path.getParent());
            Files.createFile(path);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        }
    }

    // TODO: make final once tests can work on windows without special-casing.
    @NonNull
    @Override
    public Path toPath(@NonNull File file) {
        return toPath(file.getPath());
    }

    @NonNull
    @Override
    public Path toPath(@NonNull String path) {
        return getFileSystem().getPath(path);
    }
}
