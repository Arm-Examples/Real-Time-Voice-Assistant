/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

project.ext.set("STT_MODELS_DIR", "$projectDir/../stt/stt-src/resources_downloaded/models/")
project.ext.set("LLM_MODELS_DIR", "$projectDir/../llm/llm-src/resources_downloaded/models/")
project.ext.set("IMAGES_DIR", "$projectDir/../resources/images/")
project.ext.set("CONFIG_DIR", "$projectDir/src/model_configuration_files/")
project.ext.set("DEVICE_FOLDER", "/storage/emulated/0/Android/data/com.arm.voiceassistant/files/Download/")
project.ext.set("PUSH_MODELS_PY", "$projectDir/pushAppResources.py")


apply("download.gradle")

android {
    namespace = "com.arm.voiceassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arm.voiceassistant"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Use the value prepared at the root during sync:
        buildConfigField(
            "String",
            "LLM_FRAMEWORK",
            "\"${rootProject.extra["LLM_FRAMEWORK"] as String}\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isDefault = true
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":stt"))
    implementation(project(":llm"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.google.accompanist.permissions)
    implementation(libs.google.gson)
    implementation(libs.glide.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockito.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
