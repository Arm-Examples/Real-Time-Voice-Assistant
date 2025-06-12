/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.Constants

/**
 * Properties to set for the main button, based on app content state
 * @property buttonContentDescription content description for main button
 * @property iconContentDescription icon description for main button
 * @property secondaryIconContentDescription icon description for secondary button
 * @property buttonText text to show in main button
 * @property buttonIcon icon to show in main button
 * @property buttonShape shape for main button
 * @property secondaryButtonIcon icon to show in second button e.g. cancel
 * @property iconColour colour for main button icon
 * @property animateIcon show flashing animation for icon
 * @property onButtonClick function called when main button clicked
 * @property onSecondaryButtonClick function called when secondary button clicked
 * @property enabled if main button is enabled
 * @property showTimer if timer text should be displayed
 * @property timerText timer text to show
 */
data class ButtonState(
    val buttonContentDescription: String = "",
    val iconContentDescription: String = "",
    val secondaryIconContentDescription: String = "",
    var buttonText: String = "",
    val buttonIcon: ImageVector? = null,
    val buttonShape: Shape = RoundedCornerShape(35),
    val secondaryButtonIcon: ImageVector? = null,
    val iconColour: Color,
    val animateIcon: Boolean = false,
    val onButtonClick: () -> Unit = {},
    val onSecondaryButtonClick: () -> Unit = {},
    val enabled: Boolean = true,
    val showTimer: Boolean = false,
    val timerText: String = "00:00"
)

/**
 * Main action button that changes based on app state.
 * @param modifier Layout modifier
 * @param onClickStartRecording Called to start recording
 * @param onClickStopRecording Called to stop recording
 * @param onClickCancelRecording Called to cancel an ongoing recording
 * @param onClickCancel Called to cancel processing
 * @param contentState Current app state
 * @param timerText Timer text shown during recording
 * @param animateIcon If true, mic icon blinks while recording
 */
@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    onClickStartRecording: () -> Unit = {},
    onClickStopRecording: () -> Unit = {},
    onClickCancelRecording: () -> Unit = {},
    onClickCancel: () -> Unit = {},
    contentState: Constants.ContentStates,
    timerText: String = "00:00",
    animateIcon: Boolean = false
){
    val buttonState: ButtonState
    val defaultIconColour = MaterialTheme.colorScheme.onPrimaryContainer

    // Set button states dependent on app content state
    when (contentState) {
        Constants.ContentStates.Idle -> {
            buttonState = ButtonState(
                buttonContentDescription = "record",
                iconContentDescription = "microphone",
                buttonText = "Press to talk",
                buttonIcon = Icons.Outlined.Mic,
                iconColour = defaultIconColour,
                onButtonClick = onClickStartRecording
            )
        }

        Constants.ContentStates.Recording -> {
            buttonState = ButtonState(
                buttonContentDescription = "stop_recording",
                iconContentDescription = "microphone",
                secondaryIconContentDescription = "cancel_recording",
                buttonText = "Recording... press to finish",
                buttonIcon = Icons.Outlined.Mic,
                buttonShape = RoundedCornerShape(35, 0, 0, 35),
                secondaryButtonIcon = Icons.Filled.Cancel,
                iconColour = MaterialTheme.colorScheme.tertiary,
                animateIcon = animateIcon,
                onButtonClick = onClickStopRecording,
                onSecondaryButtonClick = onClickCancelRecording,
                showTimer = true,
                timerText = timerText
            )
        }

        Constants.ContentStates.Transcribing, Constants.ContentStates.Responding, Constants.ContentStates.Speaking -> {
            buttonState = ButtonState(
                buttonContentDescription = "cancel",
                iconContentDescription = "cancel",
                buttonText = "Cancel",
                buttonIcon = Icons.Filled.Cancel,
                iconColour = defaultIconColour,
                onButtonClick = onClickCancel
            )
        }

        Constants.ContentStates.Cancelling -> {
            buttonState = ButtonState(
                buttonContentDescription = "cancelling",
                buttonText = "Cancelling...",
                iconColour = defaultIconColour,
                enabled = false
            )
        }
    }

    PipelineButton(modifier = modifier, buttonState = buttonState)
}

/**
 * Main button for app functionality e.g. to start/stop recording, to start main pipeline
 * @param modifier Layout modifier
 * @param buttonState The current button style and behavior
 */
@Composable
private fun PipelineButton (
    modifier: Modifier = Modifier,
    buttonState: ButtonState
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(35))
            .background(MaterialTheme.colorScheme.secondary)
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val buttonWidth = if (buttonState.secondaryButtonIcon != null) 0.85f else 1f
        // Main button
        Button(
            modifier = Modifier
                .zIndex(1.0f)
                .fillMaxHeight()
                .fillMaxWidth(buttonWidth)
                .semantics { contentDescription = buttonState.buttonContentDescription },
            onClick = buttonState.onButtonClick,
            shape = buttonState.buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(start = 15.dp, end = 10.dp),
            enabled = buttonState.enabled
        ) {
            // Blinking animation for record button icon
            val infiniteTransition = rememberInfiniteTransition(label = "infinite transition")
            val blinkingAnimation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500), repeatMode = RepeatMode.Reverse
                ),
                label = "blinking animation"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Main button text
                Text(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    text = buttonState.buttonText,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.weight(1f))

                // Add timer text
                if (buttonState.showTimer) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        text = buttonState.timerText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Button icon
                buttonState.buttonIcon?.let {
                    val iconModifier = Modifier.padding(start = 6.dp, end = 6.dp)
                    Icon(
                        modifier =
                        if (buttonState.animateIcon) iconModifier.alpha(blinkingAnimation)
                        else iconModifier,
                        imageVector = it,
                        contentDescription = buttonState.iconContentDescription,
                        tint = buttonState.iconColour
                    )
                }

            }
        }

        // Add second button if set
        buttonState.secondaryButtonIcon?.let {
            HorizontalDivider(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .padding(top = 4.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(
                modifier = Modifier
                    .semantics { contentDescription = buttonState.secondaryIconContentDescription },
                onClick = { buttonState.onSecondaryButtonClick() },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(0, 35, 35, 0)
            ) {
                Icon(
                    contentDescription = buttonState.secondaryIconContentDescription,
                    imageVector = buttonState.secondaryButtonIcon
                )
            }
        }

    }

}

/**
 * Launch preview
 */
@Preview(showBackground = true)
@Composable
private fun LaunchApplicationButtonPreview() {
    VoiceAssistantTheme {
        Column {
            ActionButton(
                modifier = Modifier.height(40.dp),
                contentState = Constants.ContentStates.Idle
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                modifier = Modifier.height(40.dp),
                contentState = Constants.ContentStates.Recording
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                modifier = Modifier.height(40.dp),
                contentState = Constants.ContentStates.Responding
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                modifier = Modifier.height(40.dp),
                contentState = Constants.ContentStates.Cancelling
            )
        }
    }
}
