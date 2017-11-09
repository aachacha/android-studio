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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Log interface for actions executed through {@link Bridge} and {@link RenderSession}.
 */
public interface ILayoutLog {
    /**
     * Logs a warning.
     *
     * @param tag a tag describing the type of the warning
     * @param message the message of the warning
     * @param viewCookie the view cookie where the error generated, if available
     * @param data an optional data bundle that the client can use to improve the warning display.
     */
    public default void warning(@Nullable String tag, @NonNull String message, @Nullable Object viewCookie,
            @Nullable Object data) {}

    /**
     * Logs a fidelity warning.
     *
     * <p>This type of warning indicates that the render will not be the same as the rendering on a
     * device due to limitation of the Java rendering API.
     *
     * @param tag a tag describing the type of the warning
     * @param message the message of the warning
     * @param throwable an optional Throwable that triggered the warning
     * @param viewCookie the view cookie where the error generated, if available
     * @param data an optional data bundle that the client can use to improve the warning display.
     */
    public default void fidelityWarning(@Nullable String tag, @NonNull String message, @Nullable Throwable throwable,
            @Nullable Object viewCookie, @Nullable Object data) {}

    /**
     * Logs an error.
     *
     * @param tag a tag describing the type of the error
     * @param message the message of the error
     * @param viewCookie the view cookie where the error generated, if available
     * @param data an optional data bundle that the client can use to improve the error display.
     */
    public default void error(@Nullable String tag, @NonNull String message, @Nullable Object viewCookie,
            @Nullable Object data) {}

    /**
     * Logs an error, and the {@link Throwable} that triggered it.
     *
     * @param tag a tag describing the type of the error
     * @param message the message of the error
     * @param throwable the Throwable that triggered the error
     * @param viewCookie the view cookie where the error generated, if available
     * @param data an optional data bundle that the client can use to improve the error display.
     */
    public default void error(@Nullable String tag, @NonNull String message, @Nullable Throwable throwable,
            @Nullable Object viewCookie, @Nullable Object data) {}
}
