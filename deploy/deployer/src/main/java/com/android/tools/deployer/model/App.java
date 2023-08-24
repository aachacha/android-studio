/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.deployer.model;

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.model.component.Activity;
import com.android.tools.deployer.model.component.AppComponent;
import com.android.tools.deployer.model.component.Complication;
import com.android.tools.deployer.model.component.ComponentType;
import com.android.tools.deployer.model.component.Tile;
import com.android.tools.deployer.model.component.WatchFace;
import com.android.tools.manifest.parser.components.ManifestActivityInfo;
import com.android.tools.manifest.parser.components.ManifestServiceInfo;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class App {
    static final String NO_FLAGS = "";

    private final List<Apk> apks;

    private final String appId;

    private final ILogger logger;

    // TODO: Get rid of this annoying logger parameter. This is only used for some activation
    // operations.
    // We should move the activation in an Activator object.
    private App(@NonNull String appId, @NonNull List<Apk> apks, @NonNull ILogger logger) {
        this.appId = appId;
        this.apks = apks;
        this.logger = logger;
    }

    public static App fromApks(
            @NonNull String appId, @NonNull List<Apk> apks, @NonNull ILogger logger) {
        return new App(appId, apks, logger);
    }

    public static App fromApk(@NonNull String appId, @NonNull Apk apk, @NonNull ILogger logger) {
        return new App(appId, Arrays.asList(apk), logger);
    }

    public static App fromApks(@NonNull String appId, @NonNull List<Apk> apks) {
        return new App(appId, apks, new StdLogger(StdLogger.Level.WARNING));
    }

    public static App fromString(
            @NonNull String appId, @NonNull String apkPath, @NonNull ILogger logger) {
        return new App(appId, Arrays.asList(ApkParser.parse(apkPath)), logger);
    }

    public static App fromPaths(
            @NonNull String appId, @NonNull List<Path> paths, @NonNull ILogger logger) {
        return new App(appId, convert(paths), logger);
    }

    public static App fromPath(@NonNull String appId, @NonNull Path path, @NonNull ILogger logger) {
        return fromPaths(appId, Arrays.asList(path), logger);
    }

    @NonNull
    private static List<Apk> convert(@NonNull List<Path> paths) {
        List<Apk> apks = new ArrayList<>();
        for (Path path : paths) {
            apks.add(ApkParser.parse(path.toAbsolutePath().toString()));
        }
        return apks;
    }

    public String getAppId() {
        return appId;
    }

    public List<Apk> getApks() {
        return apks;
    }

    public void activateComponent(
            @NonNull ComponentType type,
            @NonNull String componentName,
            @NonNull IShellOutputReceiver receiver,
            @NonNull IDevice device)
            throws DeployerException {
        activateComponent(type, componentName, NO_FLAGS, AppComponent.Mode.RUN, receiver, device);
    }

    public void activateComponent(
            @NonNull ComponentType type,
            @NonNull String componentName,
            @NonNull String extraFlags,
            @NonNull IShellOutputReceiver receiver,
            @NonNull IDevice device)
            throws DeployerException {
        activateComponent(type, componentName, extraFlags, AppComponent.Mode.RUN, receiver, device);
    }

    public void activateComponent(
            @NonNull ComponentType type,
            @NonNull String componentName,
            @NonNull AppComponent.Mode mode,
            @NonNull IShellOutputReceiver receiver,
            @NonNull IDevice device)
            throws DeployerException {
        activateComponent(type, componentName, NO_FLAGS, mode, receiver, device);
    }

    public void activateComponent(
            @NonNull ComponentType type,
            @NonNull String componentName,
            @NonNull String extraFlags,
            @NonNull AppComponent.Mode mode,
            @NonNull IShellOutputReceiver receiver,
            @NonNull IDevice device)
            throws DeployerException {
        String qualifiedName = componentName.startsWith(".")
                               ? appId + componentName
                               : componentName;
        AppComponent component = getComponent(type, qualifiedName);
        component.activate(extraFlags, mode, receiver, device);
    }

    @NonNull
    private AppComponent getComponent(@NonNull ComponentType type, @NonNull String qualifiedName)
            throws DeployerException {
        AppComponent component = null;
        switch (type) {
            case ACTIVITY:
                Optional<ManifestActivityInfo> optionalActivity = getActivity(qualifiedName);
                if (optionalActivity.isPresent()) {
                    component = new Activity(optionalActivity.get(), appId, logger);
                }
                break;
            case WATCH_FACE:
                Optional<ManifestServiceInfo> optionalService = getService(qualifiedName);
                if (optionalService.isPresent()) {
                    component = new WatchFace(optionalService.get(), appId, logger);
                }
                break;
            case TILE:
                optionalService = getService(qualifiedName);
                if (optionalService.isPresent()) {
                    component = new Tile(optionalService.get(), appId, logger);
                }
                break;
            case COMPLICATION:
                optionalService = getService(qualifiedName);
                if (optionalService.isPresent()) {
                    component = new Complication(optionalService.get(), appId, logger);
                }
                break;
            default:
                throw DeployerException.componentActivationException(
                        "Unsupported app component type " + type);
        }
        if (component == null) {
            throw DeployerException.componentActivationException(
                    String.format(
                            "'%s' with name '%s' is not found in '%s'",
                            type, qualifiedName, appId));
        }
        return component;
    }

    public void forceStop(@NonNull IDevice device) {
        device.forceStop(appId);
    }

    @NonNull
    private Optional<ManifestActivityInfo> getActivity(@NonNull String qualifiedName) {
        for (Apk apk : apks) {
            Optional<ManifestActivityInfo> optionalActivity = apk.activities.stream()
                    .filter(a -> a.getQualifiedName().equals(qualifiedName))
                    .findAny();
            if (optionalActivity.isPresent()) {
                return optionalActivity;
            }
        }
        return Optional.empty();
    }

    @NonNull
    private Optional<ManifestServiceInfo> getService(@NonNull String qualifiedName) {
        for (Apk apk : apks) {
            Optional<ManifestServiceInfo> optionalService = apk.services.stream()
                    .filter(a -> a.getQualifiedName().equals(qualifiedName))
                    .findAny();
            if (optionalService.isPresent()) {
                return optionalService;
            }
        }
        return Optional.empty();
    }
}
