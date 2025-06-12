/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arm.voiceassistant.VoiceAssistantApplication

/**
 * Object that provides a shared [MainViewModel] instance using a [ViewModelProvider.Factory].
 * This factory ensures the [MainViewModel] is created with the required [VoiceAssistantApplication] context.
 */
object ViewModelProvider {
    /**
     * Factory used to create an instance of [MainViewModel] with application context.
     */
    val Factory = viewModelFactory {
        // MainViewModel initialize
        initializer {
            MainViewModel(
                voiceAssistantApplication()
            )
        }
    }
}

/**
 * Extension function that retrieves the [VoiceAssistantApplication] instance from the [CreationExtras]
 * used during ViewModel initialization.
 * @return The [VoiceAssistantApplication] instance passed into the ViewModel creation.
 */
fun CreationExtras.voiceAssistantApplication(): VoiceAssistantApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VoiceAssistantApplication)
