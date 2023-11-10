/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.component.analytics

import com.android.build.api.variant.AndroidTestBuilder
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.PropertyAccessNotAllowedException
import com.android.tools.build.gradle.internal.profile.VariantMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

internal class AnalyticsEnabledApplicationVariantBuilderTest {
    @Mock
    lateinit var delegate: ApplicationVariantBuilder

    @Mock
    lateinit var androidTest: AndroidTestBuilder


    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledApplicationVariantBuilder

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        Mockito.`when`(delegate.androidTest).thenReturn(androidTest)
        proxy = AnalyticsEnabledApplicationVariantBuilder(delegate, stats)
    }

    @Test
    fun dependenciesInfo() {
        proxy.dependenciesInfo

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
                stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.VARIANT_BUILDER_DEPENDENCIES_INFO_VALUE)
    }

    @Test
    fun testFixtures() {
        proxy.enableTestFixtures = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.TEST_FIXTURES_ENABLED_VALUE)
    }

    @Test
    fun testProfileableWriteOnly() {
        proxy.profileable = true

        Truth.assertThat(stats.variantApiAccess.variantAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantAccessList.first().type
        ).isEqualTo(VariantMethodType.PROFILEABLE_ENABLED_VALUE)
        val exception = Assert.assertThrows(PropertyAccessNotAllowedException::class.java) {
            // direct call of proxy.profileable fails compilation
            // we do small workaround here
            val func = proxy::profileable
            func.get()
        }
        Truth.assertThat(exception.message).isEqualTo(
            """
                You cannot access profileable on ApplicationVariantBuilder in the [AndroidComponentsExtension.beforeVariants]
                callbacks. Other plugins applied later can still change this value, it is not safe
                to read at this stage.""".trimIndent())
    }
}
