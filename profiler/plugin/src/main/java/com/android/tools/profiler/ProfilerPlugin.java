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

package com.android.tools.profiler;

import com.android.build.api.transform.Transform;

import java.nio.charset.StandardCharsets;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

/**
 * A gradle plugin which, when applied, instruments the target Android app with support code for
 * helping profile it.
 */
public class ProfilerPlugin implements Plugin<Project> {

    private static final String PROPERTY_PROPERTIES_FILE = "android.profiler.properties";
    private static final String PROPERTY_ENABLED = "android.profiler.enabled";
    private static final String PROPERTY_SUPPORT_LIB_ENABLED
            = "android.profiler.supportLib.enabled";
    private static final String PROPERTY_INSTRUMENTATION_ENABLED
            = "android.profiler.instrumentation.enabled";

    @Override
    public void apply(final Project project) {
        Properties properties = getProperties(project);
        boolean enabled = getBoolean(properties, PROPERTY_ENABLED);
        if (enabled) {
            addProfilersLib(project, properties);
            registerTransform(project, properties);
        }
    }

    private void addProfilersLib(Project project, Properties properties) {
        if (getBoolean(properties, PROPERTY_SUPPORT_LIB_ENABLED)) {
            // Only add the lib to the application:
            if (!isApplicationProject(project)) {
                return;
            }
            project.getDependencies().add("compile", "com.android.tools:studio-profiler-lib:1.0");
        }
    }

    private void registerTransform(Project project, Properties properties) {
        if (getBoolean(properties, PROPERTY_INSTRUMENTATION_ENABLED)) {
            // TODO: The following line won't work for the experimental plugin. For that we may need to
            // register a rule that will get executed at the right time. Investigate this before
            // shipping the plugin.

            // Only apply the transformation if this is an application:
            if (!isApplicationProject(project)) {
                return;
            }

            try {
                Object android = project.getExtensions().getByName("android");
                Method registerTransform = android.getClass()
                        .getMethod("registerTransform", Transform.class, Object[].class);
                registerTransform.invoke(android, new ProfilerTransform(), new Object[]{});
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isApplicationProject(Project project) {
        Object android = project.getExtensions().getByName("android");
        try {
            // We use a heuristic to identify whether we are being applied to the application
            // or the libraries.
            android.getClass().getMethod("getApplicationVariants");
        } catch (NoSuchMethodException e) {
            return false;
        }
        return true;
    }

    private Properties getProperties(Project project) {
        Map<String, ?> projectProperties = project.getProperties();
        Properties defaults = new Properties();
        for (Map.Entry<String, ?> e : projectProperties.entrySet()) {
            // Properties extends HashTable, which does not support null values.
            if (e.getValue() != null) {
                defaults.put(e.getKey(), e.getValue());
            }
        }
        Properties result = new Properties(defaults);

        Object propertiesFile = projectProperties.get(PROPERTY_PROPERTIES_FILE);
        if (propertiesFile != null) {
            try (FileInputStream fis = new FileInputStream(String.valueOf(propertiesFile));
                 Reader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                result.load(reader);
            } catch (IOException e) {
                // Ignored.
                e.printStackTrace();
            }
        }
        return result;
    }

    private static boolean getBoolean(Properties properties, String name) {
        return Boolean.parseBoolean(properties.getProperty(name, "false"));
    }
}
