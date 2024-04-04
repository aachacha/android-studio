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

package com.google.test.inspectors.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.test.inspectors.background.BackgroundScreen
import com.google.test.inspectors.main.MainScreen
import com.google.test.inspectors.network.NetworkScreen
import com.google.test.inspectors.ui.navigation.Destination.BACKGROUND
import com.google.test.inspectors.ui.navigation.Destination.NETWORK

private enum class Destination(val screen: @Composable () -> Unit) {
  NETWORK({ NetworkScreen() }),
  BACKGROUND({ BackgroundScreen() }),
}

@Composable
fun Navigation() {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = "main") {
    composable("main") {
      MainScreen(
        onNetworkClicked = { navController.navigate(NETWORK.name) },
        onBackgroundClicked = { navController.navigate(BACKGROUND.name) },
      )
    }
    Destination.entries.forEach { destination ->
      composable(destination.name) { destination.screen() }
    }
  }
}
