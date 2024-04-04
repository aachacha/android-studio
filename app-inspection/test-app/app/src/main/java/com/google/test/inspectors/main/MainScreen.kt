/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.test.inspectors.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.test.inspectors.ui.ButtonGrid
import com.google.test.inspectors.ui.button
import com.google.test.inspectors.ui.theme.InspectorsTestAppTheme

@Composable
fun MainScreen(onNetworkClicked: () -> Unit, onBackgroundClicked: () -> Unit) {
  Scaffold(topBar = { TopBar() }) {
    Box(modifier = Modifier.padding(it)) {
      ButtonGrid {
        button("Network Actions", onNetworkClicked)
        button("Background Actions", onBackgroundClicked)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar() {
  TopAppBar(title = { Text(text = "Inspectors Test App") })
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
  InspectorsTestAppTheme { MainScreen({}, {}) }
}
