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
package com.android.tools.deploy.swapper.testapp;

public class LambdaTarget {
    public static int seqNumber = 0;

    public interface StatusGetter {
        public String getStatus(int x);
    }

    public StatusGetter getStatusGetter(int seq) {
        return x -> "LambdaTarget NOT SWAPPED " + x + ":" + seq;
    }

    public String getStatus() {
        return getStatusGetter(seqNumber).getStatus(seqNumber++);
    }
}
