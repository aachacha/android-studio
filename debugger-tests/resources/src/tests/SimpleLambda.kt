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
package tests

import breakpoint
import function

@Suppress("SameParameterValue", "unused")
object SimpleLambda {

  @JvmStatic
  fun start() {
    val i1 = 1
    val i2 = 2
    function(i2)
    foo { s ->
      val i3 = 3
      function(i1, i3, s)
      breakpoint()
    }
  }

  private fun foo(block: (String) -> Unit) {
    block("foo")
  }
}