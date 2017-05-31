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

package com.android.tools.profiler.support.util;

import android.util.Log;

/**
 * Class to call instead of Android {@link Log}.
 *
 * <p>Note that you should call these logs very sparingly, only for important events that will
 * likely only happen once. In other words, do not log any calls that have the potential to spam the
 * user's console.
 */
public final class StudioLog {
    private static final String TAG = "StudioProfiler";

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void d(String msg, Throwable tr) {
        Log.d(TAG, msg, tr);
    }

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void e(String msg, Throwable tr) {
        Log.e(TAG, msg, tr);
    }

    public static void v(String msg) {
        Log.v(TAG, msg);
    }

    public static void v(String msg, Throwable tr) {
        Log.v(TAG, msg, tr);
    }
}
