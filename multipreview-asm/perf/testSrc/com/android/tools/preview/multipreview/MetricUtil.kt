/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.preview.multipreview

import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import java.util.ArrayList

private const val NUMBER_OF_WARM_UP = 2
private const val NUMBER_OF_SAMPLES = 40
private const val MAX_PRUNED_SAMPLES = NUMBER_OF_SAMPLES / 4

private val timeBenchmark = Benchmark.Builder("Multipreview ASM Search Time Benchmark")
    .setDescription("Base line for Multipreview ASM Search time (mean) after $NUMBER_OF_SAMPLES samples.")
    .build()
private val memoryBenchmark = Benchmark.Builder("Multipreview ASM Search Memory Usage Benchmark")
    .setDescription("Base line for Multipreview ASM Search memory usage (mean) after $NUMBER_OF_SAMPLES samples.")
    .build()

internal fun computeAndRecordMetric(
    timeMetricName: String,
    memoryMetricName: String,
    computable: () -> MultipreviewMetric,
) {
    System.gc()
    // Classloading and thread pool require warm up
    repeat(NUMBER_OF_WARM_UP) {
        computable()
    }

    // baseline samples
    val times: MutableList<Metric.MetricSample> = ArrayList(NUMBER_OF_SAMPLES)
    val memoryUsages: MutableList<Metric.MetricSample> = ArrayList(NUMBER_OF_SAMPLES)
    repeat(NUMBER_OF_SAMPLES) {
        val metric = computable()
        times.add(metric.timeMetricSample)
        memoryUsages.add(metric.memoryMetricSample)
    }

    Metric(timeMetricName).apply {
        addSamples(timeBenchmark, *removeLargest(times).toTypedArray())
        commit()
    }
    // Let's start without pruning to see how bad it is.
    Metric(memoryMetricName).apply {
        addSamples(memoryBenchmark, *memoryUsages.toTypedArray())
        commit()
    }
}

/**
 * Remove the largest time measurements as they are outliers. One cannot measure the time that it
 * less than it actually took to run, however larger measurements are possible. Remove them,
 * assuming they do not happen too often.
 */
private fun removeLargest(l: List<Metric.MetricSample>): List<Metric.MetricSample> {
    return l.sortedBy { it.sampleData }.take(NUMBER_OF_SAMPLES - MAX_PRUNED_SAMPLES)
}
