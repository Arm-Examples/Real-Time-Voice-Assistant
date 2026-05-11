/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.arm.llm"

    defaultConfig {
        externalNativeBuild {
            cmake {
                targets += "llm-jni"
                targets += "llm-jni-stage-shared-libraries"

                val llmFramework = rootProject.extensions.extraProperties.get("LLM_FRAMEWORK") as String
                logger.lifecycle("Using LLM framework: $llmFramework")

                arguments += "-DLLM_FRAMEWORK=$llmFramework"
            }
        }

        sourceSets {
            getByName("main") {
                java.srcDir("llm-src/src/java")
            }
        }
    }
}
