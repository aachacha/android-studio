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
import androidx.inspection.InspectorEnvironment;
import androidx.inspection.InspectorFactory;
import java.util.List;

// this factory accesses environment on createInspector call.
public class TestEnvironmentInspectorFactory extends InspectorFactory<TestInspector> {

    public TestEnvironmentInspectorFactory() {
        super("test.environment.inspector");
    }

    @Override
    public TestInspector createInspector(Connection connection, InspectorEnvironment environment) {
        environment.findInstances(TestInspector.class);
        environment.registerEntryHook(
                TestInspectorFactory.class,
                "_",
                new InspectorEnvironment.EntryHook() {
                    @Override
                    public void onEntry(Object o, List<Object> list) {}
                });
        environment.registerExitHook(
                TestInspectorFactory.class,
                "_",
                new InspectorEnvironment.ExitHook<String>() {
                    @Override
                    public String onExit(String s) {
                        return s;
                    }
                });
        return new TestInspector(connection);
    }
}
