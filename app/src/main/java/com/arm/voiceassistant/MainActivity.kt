/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.MODIFY_AUDIO_SETTINGS
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.ToastService
import com.arm.voiceassistant.viewmodels.MainViewModel


/**
 * Main activity referenced in AndroidManifest.xml which sets up the screens
 *
 * This activity handles:
 * - UI composition using Jetpack Compose
 * - Permission handling for microphone access
 * - Initializing and retaining the main view model instance
 */
class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel                       // Save the mainViewModel for the post initialized parts
    private val permissions = arrayOf(RECORD_AUDIO, MODIFY_AUDIO_SETTINGS)  // Permissions required
    private val requestPermissionLauncher = registerForActivityResult(      // Permission request launcher
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allPermissionsGranted = permissions.entries.all { it.value }
        if (!allPermissionsGranted) {
            /**
             * Handle the denied permission requests as the permission request intent will not
             * pop up after the user denied the permission request twice. If we finish the app here,
             * the app will be finished immediately without any instructions after the permission is denied twice.
             * This handler will navigate the user to 'Settings' and get the permission setup according to the instructions.
             */
            handlePermissionsNotGranted(permissions)
        }
    }

    /**
     * Called when the activity is created. Initializes the UI and launches the permission request.
     * @param savedInstanceState Saved state bundle, if available
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoiceAssistantTheme {

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Save the context
                    AppContext.getInstance().context = this
                    // Initialize toast
                    ToastService.initialize(AppContext.getInstance().context!!)

                    // Build the screen and return the mainViewModel for post init
                    // Pass imageUploadAction composable
                    mainViewModel = screenScaffold()

                    // Check the permissions
                    requestPermissionLauncher.launch(permissions)
                }
            }
        }
    }

    /**
     * Displays a dialog when permissions are permanently denied and navigates
     * the user to the app's settings page to manually grant them.
     * @param permissions Map of permission results from the launcher
     */
    private fun handlePermissionsNotGranted(permissions: Map<String, Boolean>) {
        val permanentlyDeniedPermissions = permissions.filter { !it.value && !shouldShowRequestPermissionRationale(it.key) }
        if (permanentlyDeniedPermissions.isNotEmpty()) {
            // Show a dialog explaining why the permissions are needed and provide a way to open app settings
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("The Voice Assistant requires 'Microphone' permissions to function properly. Please enable them in the Settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    // If the user choose 'Cancel', the app will be closed
                    finish()
                }
                .show()
        } else {
            // Show a message explaining why the permissions are needed
            ToastService.showToast(Constants.RECORD_PERMISSION_ERROR)
        }
    }
}

class VoiceAssistantApplication : Application()
