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
package com.android.tools.debuggertests

import com.android.tools.debuggertests.Engine.EngineType
import com.android.tools.debuggertests.Engine.EngineType.JVM
import com.android.tools.debuggertests.Engine.EngineType.SIMPLE
import kotlin.time.Duration.Companion.seconds
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.optional
import kotlinx.cli.vararg
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Updates golden files.
 *
 * Run with `bazel run //tools/base/debugger-tests:update-golden`
 *
 * When running from Intellij, add to VM options:
 * ```
 * --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED
 * ```
 */
fun main(args: Array<String>) {
  val parser = ArgParser("UpdateGolden")
  val verbose by parser.option(ArgType.Boolean, shortName = "v").default(false)
  val type by parser.option(ArgType.Choice<EngineType>(), shortName = "t").default(SIMPLE)
  val tests by parser.argument(ArgType.String).vararg().optional()
  parser.parse(args)

  val testClasses = tests.takeIf { it.isNotEmpty() } ?: Resources.findTestClasses()
  testClasses.forEach { testClass ->
    println("Test $testClass")
    val engine =
      when (type) {
        SIMPLE -> SimpleEngine(testClass)
        JVM -> JvmEngine(testClass)
      }
    engine.use {
      val actual = runBlocking { withTimeout(30.seconds) { engine.runTest() } }
      Resources.writeGolden(testClass, actual)
      if (verbose) {
        println(actual)
      }
    }
  }
}