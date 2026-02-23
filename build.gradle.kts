/*
 * SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

apply(from = rootProject.file("llm-config.gradle.kts"))

subprojects {
    // Only apply this configuration if the subproject applies the Android library plugin.
    plugins.withId("com.android.library") {
        // Import the Android library extension type.
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            compileSdk = 34

            defaultConfig {
                minSdk = 33

                ndkVersion = "29.0.14206865"

                externalNativeBuild {
                    cmake {
                        arguments(
                            "-DBUILD_JNI_LIB=ON",
                            "-DBUILD_LLM_TESTING=OFF"
                        )

                        //Check if KleidiAI needs to be disabled
                        val kleidiAiDisabled = project.findProperty("kleidiAI") == "false"

                        //Disable KleidiAI
                        if (kleidiAiDisabled) {
                            arguments += "-DUSE_KLEIDIAI=OFF"
                        }
                    }
                }

                val abiFilter: String? by project

                ndk {
                    abiFilters.clear()
                    if (abiFilter != null) {
                        abiFilters.add(abiFilter!!)
                    } else {
                        abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
                    }
                }
            }

            buildTypes {
                getByName("release") {
                    isDefault = true
                    isMinifyEnabled = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )

                    externalNativeBuild {
                        cmake {
                            arguments += "-DCMAKE_BUILD_TYPE=Release"
                        }
                    }
                }
                getByName("debug") {
                    isJniDebuggable = true
                }
            }

            externalNativeBuild {
                cmake {
                    path = file("CMakeLists.txt")
                    version = "3.27.0+"
                }
            }
        }
    }
}
