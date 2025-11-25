/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

val validLLMFrameworks = listOf("llama.cpp", "onnxruntime-genai", "mnn")

// Get the user-defined LLM framework from CLI (-PllmFramework=...) or gradle.properties
val llmFramework: String = project.findProperty("llmFramework").toString()

// Validate the provided framework
require(llmFramework in validLLMFrameworks) {
    "Invalid llmFramework specified: '$llmFramework'. Must be one of: ${validLLMFrameworks.joinToString()}"
}

// Make it available to all subprojects
rootProject.extensions.extraProperties.set("LLM_FRAMEWORK", llmFramework)
