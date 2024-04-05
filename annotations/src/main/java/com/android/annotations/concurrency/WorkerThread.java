/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.annotations.concurrency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the target method will always run on a worker thread, i.e., a thread <i>other</i>
 * than the UI thread. Due to thread safety concerns, methods running on a worker thread should
 * <i>not</i> call {@link UiThread} methods. Conversely, {@link UiThread} methods should not call
 * {@link WorkerThread} methods.
 *
 * <p>If the annotated element is a class, then this indicates that all of the methods in the class
 * will be called on a worker thread.
 *
 * <p>For methods which may sometimes run on a worker thread and sometimes on the UI thread, please
 * use the {@link AnyThread} annotation instead.
 *
 * @see UiThread
 * @see AnyThread
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.TYPE,
    ElementType.PARAMETER,
    ElementType.TYPE_USE
})
public @interface WorkerThread {}
