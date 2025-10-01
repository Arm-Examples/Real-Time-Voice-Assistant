/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
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
                targets += listOf("arm-stt-jni")
                arguments += "-DBUILD_SHARED_LIBS=OFF"
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
