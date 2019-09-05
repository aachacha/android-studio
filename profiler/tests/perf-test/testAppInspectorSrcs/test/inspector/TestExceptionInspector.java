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

package test.inspector;

import androidx.inspection.Connection;
import androidx.inspection.Inspector;
import java.util.Arrays;

public class TestExceptionInspector extends Inspector {
    public TestExceptionInspector(Connection connection) {
        super(connection);
        System.out.println("TEST INSPECTOR CREATED");
    }

    @Override
    public void onDispose() {
        System.out.println("TEST INSPECTOR DISPOSED");
    }

    @Override
    public void onReceiveCommand(byte[] bytes, CommandCallback commandCallback) {
        System.out.println("TEST INSPECTOR " + Arrays.toString(bytes));
        throw new RuntimeException("This is an inspector exception.");
    }
}
