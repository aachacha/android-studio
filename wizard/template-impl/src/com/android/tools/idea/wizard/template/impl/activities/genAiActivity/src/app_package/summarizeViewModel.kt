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
package com.android.tools.idea.wizard.template.impl.activities.genAiActivity.src.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun summarizeViewModelKt(
    packageName: String,
) = """
package ${escapeKotlinIdentifier(packageName)}

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SummarizeViewModel(
    private val generativeModel: GenerativeModel
) : ViewModel() {

    private val _uiState: MutableStateFlow<SummarizeUiState> =
        MutableStateFlow(SummarizeUiState.Initial)
    val uiState: StateFlow<SummarizeUiState> =
        _uiState.asStateFlow()

    fun summarize(inputText: String) {
        _uiState.value = SummarizeUiState.Loading

        val prompt = "Summarize the following text for me: ${"$"}inputText"

        viewModelScope.launch {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text?.let { outputContent ->
                    _uiState.value = SummarizeUiState.Success(outputContent)
                }
            } catch (e: Exception) {
                _uiState.value = SummarizeUiState.Error(e.localizedMessage ?: "")
            }
        }
    }
}
"""
