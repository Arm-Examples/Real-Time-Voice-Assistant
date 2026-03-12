#
# SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

cmake_minimum_required(VERSION 3.27.0)

project(llm-module
        DESCRIPTION "LLM Module"
        LANGUAGES C CXX ASM)

include(FetchContent)

set(LLM_MODULE_GIT_URL "https://github.com/Arm-Examples/LLM-Runner.git")
set(LLM_MODULE_GIT_SHA "f12fbb4e5516d2cd9af336f260d37d1a39b25ec2")
set(LLM_MODULE_SRC_DIR "${CMAKE_SOURCE_DIR}/llm-src")

FetchContent_Declare(
llmModule
GIT_REPOSITORY     ${LLM_MODULE_GIT_URL}
GIT_TAG            ${LLM_MODULE_GIT_SHA}
SOURCE_DIR         ${LLM_MODULE_SRC_DIR}
)

FetchContent_MakeAvailable(llmModule)

# This block of code is required to copy llama.ccp arch variant shared libraries
# to a known location so that they can be picked up by gradle build
# and packaged with the APK.
# This approach may be used by other backends in future so there is no check for
# which backend is being used.
if (DEFINED ANDROID_ABI)
   set(SHARED_LIBS_STAGED_DIR "${CMAKE_SOURCE_DIR}/build/staged/${ANDROID_ABI}")

   add_custom_target(arm-llm-jni-stage-shared-libraries ALL
           COMMAND ${CMAKE_COMMAND} -E echo "Staging llama.cpp arch-variant shared libs"
           COMMAND ${CMAKE_COMMAND} -E make_directory "${SHARED_LIBS_STAGED_DIR}"
           COMMAND ${CMAKE_COMMAND} -E copy_directory
           "${CMAKE_BINARY_DIR}/lib" "${SHARED_LIBS_STAGED_DIR}"
           COMMENT "Copying shared libs to ${SHARED_LIBS_STAGED_DIR}"
   )
   add_dependencies(arm-llm-jni-stage-shared-libraries arm-llm-jni)
endif()
