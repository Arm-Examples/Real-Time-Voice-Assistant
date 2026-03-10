/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
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
project.ext.set("CONFIG_DIR_LLM", "$projectDir/../llm/llm-src/model_configuration_files/")
project.ext.set("CONFIG_DIR_STT", "$projectDir/../stt/stt-src/model_configuration_files/")
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
        jniLibs {
            useLegacyPackaging = true

            // Need to filter out the duplicate ggml shared libs from Whisper/llama.cpp, otherwise gradle build
            // will detect duplicates libs and throw a fatal error during build of APK
            pickFirsts.add("**/libggml*.so")
        }
    }
    // Need to filter out the ggml shared libs from Whisper when backend is llama.cpp, otherwise gradle build
    // will detect duplicate shared libs and throw a fatal error during build of APK
    val sttStagedSharedLibraryPath = project(":stt").layout.buildDirectory.dir("staged")
    val sttFilteredSharedLibraryPath = project(":stt").layout.buildDirectory.dir("stagedFiltered")
    val isLlama = rootProject.extra["LLM_FRAMEWORK"]?.toString() == "llama.cpp"
    if (isLlama) {
        val copySttFiltered by tasks.registering(Copy::class) {
            from(sttStagedSharedLibraryPath)
            into(sttFilteredSharedLibraryPath)
            exclude("**/libggml*.so")
        }
        tasks.named("preBuild").configure {
            dependsOn(copySttFiltered)
        }
    }
    val llmStagedSharedLibraryPath = project(":llm").layout.buildDirectory.dir("staged")
    sourceSets {
        getByName("main").jniLibs.srcDirs(
            llmStagedSharedLibraryPath,
            if(isLlama) {
                sttFilteredSharedLibraryPath
            } else {
                sttStagedSharedLibraryPath
            }
        )
    }
}

androidComponents {

    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        // Need to make sure that the shared libs are merged after LLM is built otherwise
        // the libs will be built after the jni merge is complete and they won't be packaged
        // in the APK
        listOf("merge${cap}JniLibs", "merge${cap}JniLibFolders").forEach { mergeName ->
            tasks.matching { it.name in setOf("merge${cap}JniLib", "merge${cap}JniLibFolders") }
                .configureEach {
                    dependsOn(project(":stt").tasks.named("buildCMake${cap}"))
                    dependsOn(project(":llm").tasks.named("buildCMake${cap}"))
                }
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
    androidTestImplementation(libs.androidx.test.rules)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
