#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

cmake_minimum_required(VERSION 3.27.0)

project(stt-module
        DESCRIPTION "STT Module"
        LANGUAGES C CXX ASM)

include(FetchContent)

set(STT_MODULE_GIT_URL "https://gitlab.arm.com/kleidi/kleidi-examples/speech-to-text")
set(STT_MODULE_GIT_SHA "ee8f4da7")
set(STT_MODULE_SRC_DIR "${CMAKE_SOURCE_DIR}/stt-src")

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+dotprod+i8mm+fp16")
   set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod+i8mm+fp16")
   set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16")
endif()

FetchContent_Declare(
        sttModule
        GIT_REPOSITORY     ${STT_MODULE_GIT_URL}
        GIT_TAG            ${STT_MODULE_GIT_SHA}
        SOURCE_DIR         ${STT_MODULE_SRC_DIR}
)
FetchContent_MakeAvailable(sttModule)

