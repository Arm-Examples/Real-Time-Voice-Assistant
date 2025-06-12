/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.Constants

/**
 * Values to use for user and voice assistant's text fields
 * @property colors TextFieldColors to use
 * @property label label text to show
 * @property shape text box shape
 */
data class TextFieldStyle(
    val colors: TextFieldColors,
    val label: String,
    val shape: Shape
)

/**
 * Provides default styling for the user's text input field.
 * @param colors Color settings for the disabled text field
 * @param label Label to display above the field (default: "User")
 * @param shape Shape of the text field corners
 */
@Composable
fun userTextFieldDefaults(
    colors: TextFieldColors = TextFieldDefaults.colors(
        disabledIndicatorColor = Color.Transparent,
        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
        disabledTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    label: String = "User",
    shape: Shape = RoundedCornerShape(0, 5, 5, 5)
) = TextFieldStyle(colors, label, shape)

/**
 * Provides default styling for the voice assistant's text output field.
 * @param colors Color settings for the disabled text field
 * @param label Label to display above the field (default: "Voice Assistant")
 * @param shape Shape of the text field corners
 */
@Composable
fun voiceAssistantTextFieldDefaults(
    colors: TextFieldColors = TextFieldDefaults.colors(
        disabledIndicatorColor = Color.Transparent,
        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        disabledTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
    ),
    label: String = Constants.VOICE_ASSISTANT_TAG,
    shape: Shape = RoundedCornerShape(5, 0, 5, 5)
) = TextFieldStyle(colors, label, shape)

/**
 * Displays a styled text field with an optional user or assistant icon.
 * @param modifier Optional layout modifier
 * @param cardAspectRatio Aspect ratio for the text field card
 * @param textFieldContentDescription Content description for accessibility
 * @param label Label shown above the text field
 * @param text The text content to display
 * @param isTalking Whether the user is actively speaking
 * @param isTranscribing Whether transcription is in progress
 * @param playingAudio Whether audio playback is active
 * @param isVoiceAssistant If true, displays assistant icon on the right
 * @param shape Shape of the text field card
 * @param colors Text field colors for background and content
 */
@Composable
fun ConversationText(
    modifier: Modifier = Modifier,
    cardAspectRatio: Float = 1f,
    textFieldContentDescription: String = "",
    label: String = "",
    text: String = "",
    isTalking: Boolean = false,
    isTranscribing: Boolean = false,
    playingAudio: Boolean = false,
    isVoiceAssistant: Boolean = false,
    shape: Shape = userTextFieldDefaults().shape,
    colors: TextFieldColors = userTextFieldDefaults().colors
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        // Scroll to bottom of the text field as default
        val scrollState = rememberScrollState()

        LaunchedEffect(scrollState.maxValue) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        val userIcon: ImageVector
        val userIconDesc: String

        if (isTalking) {
            userIcon = Icons.Rounded.RecordVoiceOver
            userIconDesc = "RecordVoiceOver"
        }
        else {
            userIcon = Icons.Rounded.Person
            userIconDesc = "Person"
        }

        // Border highlighting control
        var borderStroke: BorderStroke? = null
        val borderColor = Color.White
        var borderWidth = 0
        if (isTalking || isTranscribing) {
            borderWidth = 4
            borderStroke = BorderStroke(borderWidth.dp, borderColor)
        }

        val showSpeakerButton = isVoiceAssistant && text.isNotEmpty()

        if (!isVoiceAssistant) {
            UserIcon(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .semantics { contentDescription = "user_icon" },
                userIcon = userIcon,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                borderWidth = (borderWidth / 2),
                borderColor = borderColor,
                contentDescription = userIconDesc
            )
        }

        Card(
            modifier = Modifier
                .padding(top = 30.dp)
                .aspectRatio(cardAspectRatio)
                .weight(1f),
            shape = shape,
            border = borderStroke,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            TextField(
                enabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .semantics { contentDescription = textFieldContentDescription },
                value = text,
                onValueChange = {},
                label = {
                    if (showSpeakerButton) {
                        LabelWithSpeaker(label, playingAudio)
                    }
                    else {
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                colors = colors,
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp
                ),
            )
        }

        if (isVoiceAssistant) {
            // Mirror icons for voice assistant
            val mirrorModifier = Modifier.scale(scaleX = -1f, scaleY = 1f)
            Column {
                UserIcon(
                    modifier = mirrorModifier
                        .padding(end = 10.dp)
                        .semantics { contentDescription = "voice_assistant_icon" },
                    userIcon = userIcon,
                    iconColor = MaterialTheme.colorScheme.primaryContainer,
                    borderWidth = (borderWidth / 2),
                    borderColor = borderColor,
                    contentDescription = userIconDesc
                )
            }
        }
    }
}

/**
 * User icon to display next to text field, dependent on app content state
 * @param modifier Modifier for layout customization
 * @param userIcon The icon to display (e.g. person or mic)
 * @param iconColor Icon tint color
 * @param iconSize Size of the icon inside the circle
 * @param borderWidth Width of the circular border (0 for no border)
 * @param borderColor Color of the border
 * @param contentDescription Description for accessibility
 */
@Composable
private fun UserIcon(
    modifier: Modifier = Modifier,
    userIcon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconSize: Dp = 60.dp,
    borderWidth: Int = 0,
    borderColor: Color = Color.White,
    contentDescription: String = ""
) {
    Column(
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = borderWidth.dp,
                color = if (borderWidth == 0) Color.Transparent else borderColor,
                shape = CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier
                .size(iconSize)
                .padding(8.dp),
            imageVector = userIcon,
            contentDescription = contentDescription,
            tint = iconColor
        )
    }
}

/**
 * Add speaker icon button to label for VoiceAssistant's text box
 * @param label The label text to display (e.g. "Voice Assistant")
 * @param playingAudio If true, shows a muted icon; otherwise a volume icon
 */
@Composable
private fun LabelWithSpeaker(
    label: String = "",
    playingAudio: Boolean = false
) {
    Text(
        text = buildAnnotatedString {
            append(label)
            appendInlineContent("speaker_icon", "speaker_icon")
        },
        inlineContent = mapOf(
            Pair("speaker_icon", InlineTextContent(
                Placeholder(
                    width = 25.sp,
                    height = 25.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                IconButton(
                    onClick = { }
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "speaker_icon",
                        imageVector = if (playingAudio) Icons.AutoMirrored.Outlined.VolumeOff
                        else Icons.AutoMirrored.Outlined.VolumeUp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } )
        ),
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    )
}

/**
 * Conversation text preview
 */
@Preview
@Composable
private fun ConversationTextPreview() {
    VoiceAssistantTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.primary)) {
            val userText = "Example transcription of speech from user."
            val responseText = "Example response from Voice Assistant."
            ConversationText(
                label = userTextFieldDefaults().label,
                text = userText,
                isTalking = false,
                isVoiceAssistant = false,
                shape = userTextFieldDefaults().shape,
                colors = userTextFieldDefaults().colors
            )
            ConversationText(
                label = voiceAssistantTextFieldDefaults().label,
                text = responseText,
                isTalking = true,
                isVoiceAssistant = true,
                shape = voiceAssistantTextFieldDefaults().shape,
                colors = voiceAssistantTextFieldDefaults().colors
            )
        }
    }
}
