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
    namespace = "com.arm.stt"


    defaultConfig {
        externalNativeBuild {
            cmake {
                targets += listOf("arm-stt-jni",  "arm-stt-jni-stage-shared-libraries" )
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DANDROID_ABI=arm64-v8a"
                arguments += "-DANDROID_PLATFORM=android-33"
            }
        }

        sourceSets{

            getByName("main"){
                java {
                    srcDir("stt-src/src/java")
                }
            }
        }
    }
}
