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

package com.android.build.gradle.internal.tasks.featuresplit;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileCollection;

/** Information containing the base split application ID. */
public class FeatureSplitApplicationId {

    @VisibleForTesting static final String PERSISTED_FILE_NAME = "application-id.json";

    @NonNull private final String applicationId;

    public FeatureSplitApplicationId(@NonNull String applicationId) {
        this.applicationId = applicationId;
    }

    @NonNull
    public String getApplicationId() {
        return applicationId;
    }

    public void save(@NonNull File outputDirectory) throws IOException {
        File outputFile = new File(outputDirectory, PERSISTED_FILE_NAME);
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        FileUtils.write(outputFile, gson.toJson(this));
    }

    @NonNull
    public static FeatureSplitApplicationId load(@NonNull FileCollection input) throws IOException {
        File persistedFile = getOutputFile(input);
        if (persistedFile == null) {
            throw new FileNotFoundException("No application declaration present.");
        }
        return load(persistedFile);
    }

    @NonNull
    public static FeatureSplitApplicationId load(@NonNull File input) throws FileNotFoundException {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        return gson.fromJson(new FileReader(input), FeatureSplitApplicationId.class);
    }

    @Nullable
    private static File getOutputFile(@NonNull FileCollection input) {
        for (File file : input.getAsFileTree().getFiles()) {
            if (file.getName().equals(PERSISTED_FILE_NAME)) {
                return file;
            }
        }
        return null;
    }

    @NonNull
    public static File getOutputFile(@NonNull File directory) {
        return new File(directory, PERSISTED_FILE_NAME);
    }
}
