#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

cmake_minimum_required(VERSION 3.27.0)

project(llm-module
        DESCRIPTION "LLM Module"
        LANGUAGES C CXX ASM)

include(FetchContent)

set(LLM_MODULE_GIT_URL "https://gitlab.arm.com/kleidi/kleidi-examples/large-language-models")
set(LLM_MODULE_GIT_SHA "832713b5")
set(LLM_MODULE_SRC_DIR "${CMAKE_SOURCE_DIR}/llm-src")

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
   set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+dotprod+i8mm")
   set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod+i8mm")
   set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm")
endif()

FetchContent_Declare(
llmModule
GIT_REPOSITORY     ${LLM_MODULE_GIT_URL}
GIT_TAG            ${LLM_MODULE_GIT_SHA}
SOURCE_DIR         ${LLM_MODULE_SRC_DIR}
)

FetchContent_MakeAvailable(llmModule)

