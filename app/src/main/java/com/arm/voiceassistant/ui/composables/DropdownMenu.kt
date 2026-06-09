/*
 * SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme

/**
 * Internal generic dropdown built on Material3 ExposedDropdownMenu.
 *
 * Displays a read-only text field showing the selected value and a dropdown
 * menu for choosing from the provided options.
 *
 * @param label Label displayed in the text field
 * @param options List of selectable values
 * @param selected Currently selected value
 * @param onSelected Callback invoked when a value is selected
 * @param modifier Optional modifier for layout customization
 * @param enabled Whether the dropdown is interactive
 * @param valueText Text shown for the currently selected value
 * @param itemText Text shown for each dropdown item
 * @param fieldTag Optional test tag for the dropdown field
 * @param optionEnabled Whether an individual option is selectable
 * @param optionTag Optional test tag for each dropdown item
 * @param infoText Optional helper text toggled from the info icon
 * @param infoTag Optional test tag for the info icon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> BaseDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueText: (T) -> String = { it.toString() },
    itemText: (T) -> String = { it.toString() },
    fieldTag: String? = null,
    optionEnabled: (T) -> Boolean = { true },
    optionTag: ((T) -> String)? = null,
    infoText: String? = null,
    infoTag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (enabled) expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = valueText(selected),
                    onValueChange = { /* readOnly */ },
                    readOnly = true,
                    enabled = enabled,
                    singleLine = true,
                    label = {
                        Text(
                            text = label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable, enabled)
                        .fillMaxWidth()
                        .then(fieldTag?.let { Modifier.testTag(it) } ?: Modifier)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { opt ->
                        val isOptionEnabled = optionEnabled(opt)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = itemText(opt),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            enabled = isOptionEnabled,
                            modifier = optionTag?.let { Modifier.testTag(it(opt)) } ?: Modifier,
                            onClick = {
                                onSelected(opt)
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (infoText != null) {
                InfoTooltipButton(
                    text = infoText,
                    modifier = Modifier
                        .align(Alignment.TopEnd),
                    testTag = infoTag,
                    offsetX = 6.dp,
                    offsetY = (-4).dp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BaseDropdownPreview() {
    VoiceAssistantTheme(darkTheme = false) {
        var selected by remember { mutableStateOf("1") }
        Column(modifier = Modifier.padding(16.dp)) {
            BaseDropdown(
                label = "Test dropdown menu",
                options = listOf("1", "2", "3", "4"),
                selected = selected,
                onSelected = { selected = it },
                infoText = "Information text for dropdown menu preview."
            )
        }
    }
}
