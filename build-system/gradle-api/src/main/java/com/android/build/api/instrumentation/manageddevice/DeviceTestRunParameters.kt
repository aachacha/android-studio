/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.instrumentation.manageddevice

import org.gradle.api.Incubating
import org.gradle.api.file.RegularFileProperty

// TODO(b/260721648) add more information on how the output is supposed to be formatted.
/**
 * All parameters for a [DeviceTestRunTaskAction].
 *
 * @param InputT The specialized input type for the custom managed device associated
 *     with the [DeviceTestRunTaskAction], generated by the corresponding
 *     [DeviceTestRunConfigureAction]
 *
 * @suppress Do not use from production code. All properties in this interface are exposed for
 * prototype.
 */
@Incubating
interface DeviceTestRunParameters<InputT: DeviceTestRunInput> {

    /**
     * All inputs specific to the Custom Managed Device type created
     * by a [DeviceTestRunConfigureAction].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val deviceInput: InputT

    /**
     * The output from the [DeviceSetupTaskAction] for managed device the test is
     * being run on.
     *
     * This input into the [TaskAction][DeviceTestRunTaskAction] is optional and will
     * be not be present if the implementation of the Custom Managed Device does not
     * implement the [ManagedDeviceSetupFactory].
     */
    @get: Incubating
    val setupResult: RegularFileProperty

    /**
     * All inputs for the Test Run independent of the type of managed device.
     *
     * See [TestRunData].
     *
     * @suppress Do not use from production code. This API is exposed for prototype.
     */
    @get: Incubating
    val testRunData: TestRunData
}
