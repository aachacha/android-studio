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

package com.android.tools.deploy.instrument;

import static com.android.tools.deploy.instrument.ReflectionHelpers.*;

import android.content.pm.ApplicationInfo;
import android.util.Log;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class ActivityThreadInstrumentation {
    // ApplicationThreadConstants.PACKAGE_REPLACED
    private static final int PACKAGE_REPLACED = 3;
    private static final String TAG = "studio.deploy";

    private static boolean mRestart;
    private static Object mActivityThread;
    private static int mCmd;

    // Set of all previous installation locations of the package.
    private static final HashSet<Path> oldPackagePaths = new HashSet<>();

    // Current installation path of the running package.
    private static Path currentPackagePath;

    public static void setRestart(boolean restart) {
        mRestart = restart;
    }

    // This method instruments DexPathList$Element#findResource(). It checks to see if this Element
    // object refers to an old installation of this package, and modifies the element to point to
    // the latest installation path if so.
    public static void handleFindResourceEntry(Object element, String name) {
        try {
            File file = (File) getDeclaredField(element, "path");
            Path dir = file.getParentFile().toPath();

            // First check if this Element points to the current package path. If so, we don't need
            // to do anything. We check this first because currentPackagePath can end up in the
            // old paths set without the package actually having been moved.
            if (currentPackagePath.equals(dir)) {
                return;
            }

            // If the path pointed to by this Element is an old installation location, bring the
            // Element up to date and mark it as uninitialized. This will cause the classloader to
            // read the new path.
            if (oldPackagePaths.contains(dir)) {
                File newFile = currentPackagePath.resolve(file.getName()).toFile();
                setDeclaredField(element, "path", newFile);
                setDeclaredField(element, "initialized", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Excepton", e);
        }
    }

    public static void handleDispatchPackageBroadcastEntry(
            Object activityThread, int cmd, String[] packages) {
        Log.v(
                TAG,
                String.format(
                        "Package broadcast entry hook { cmd=%d, time=%d, thread=%d }",
                        cmd, LocalTime.now().toNanoOfDay(), Thread.currentThread().getId()));

        mActivityThread = activityThread;
        mCmd = cmd;

        try {
            oldPackagePaths.add(getPackagePath(mActivityThread));
        } catch (Exception e) {
            Log.e(TAG, "Error in package installer patch", e);
        }
    }

    public static void handleDispatchPackageBroadcastExit() {
        Log.v(
                TAG,
                String.format(
                        "Package broadcast exit hook { cmd=%d, time=%d, thread=%d }",
                        mCmd, LocalTime.now().toNanoOfDay(), Thread.currentThread().getId()));

        if (mCmd != PACKAGE_REPLACED) {
            return;
        }

        try {
            // Update the application and activity context objects to properly point to the new
            // LoadedApk that was created by the package update. We fix activity contexts even if
            // the activities wil be restarted, as those contexts may still be in use by app code.
            Log.v(TAG, "Fixing application and activity contexts");
            Object newResourcesImpl = fixAppContext(mActivityThread);
            for (Object activity : getActivityClientRecords(mActivityThread)) {
                fixActivityContext(activity, newResourcesImpl);
            }

            if (mRestart) {
                updateApplicationInfo(mActivityThread);
            }

            currentPackagePath = getPackagePath(mActivityThread);
        } catch (Exception ex) {
            // The actual risks of the patch are unknown; although it seems to be safe, we're using some
            // defensive exception handling to prevent any application hard-crashes.
            Log.e(TAG, "Error in package installer patch", ex);
        } finally {
            mRestart = false;
        }
    }

    public static Path getPackagePath(Object activityThread) throws Exception {
        Object boundApplication = getDeclaredField(activityThread, "mBoundApplication");
        Object appInfo = getDeclaredField(boundApplication, "appInfo");
        Object packageName = getField(appInfo, "packageName");
        Object loadedApk =
                call(activityThread, "peekPackageInfo", arg(packageName), arg(true, boolean.class));
        ApplicationInfo info = (ApplicationInfo) call(loadedApk, "getApplicationInfo");
        return Paths.get(info.sourceDir.substring(0, info.sourceDir.lastIndexOf("/")));
    }

    // ResourcesImpl fixAppContext(ActivityThread activityThread)
    public static native Object fixAppContext(Object activityThread);

    // Collection<ActivityClientRecord> getActivityClientRecords(ActivityThread activityThread)
    public static native Collection<? extends Object> getActivityClientRecords(
            Object activityThread);

    // void fixActivityContext(ActivityClientRecord activityRecord, ResourcesImpl newResourcesImpl)
    public static native void fixActivityContext(Object activityRecord, Object newResourcesImpl);

    // Wrapper around ActivityThread#handleUpdateApplicationInfo(ApplicationInfo)
    public static native void updateApplicationInfo(Object activityThread);
}

