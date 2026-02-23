#
# SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

cmake_minimum_required(VERSION 3.27.0)

project(stt-module
        DESCRIPTION "STT Module"
        LANGUAGES C CXX ASM)

include(FetchContent)

set(STT_MODULE_GIT_URL "https://github.com/Arm-Examples/STT-Runner.git")
set(STT_MODULE_GIT_SHA "ac3e43d201553c8f3e51ce8347879aa0ca843826")
set(STT_MODULE_SRC_DIR "${CMAKE_SOURCE_DIR}/stt-src")

FetchContent_Declare(
        sttModule
        GIT_REPOSITORY     ${STT_MODULE_GIT_URL}
        GIT_TAG            ${STT_MODULE_GIT_SHA}
        SOURCE_DIR         ${STT_MODULE_SRC_DIR}
)
FetchContent_MakeAvailable(sttModule)

# This block of code is required to copy whisper.ccp shared libraries
# to a known location so that they can be picked up by gradle build
# and packaged with the APK.
set(SHARED_LIBS_STAGED_DIR "${CMAKE_SOURCE_DIR}/build/staged/${ANDROID_ABI}")
add_custom_target(arm-stt-jni-stage-shared-libraries ALL
        COMMAND ${CMAKE_COMMAND} -E echo "Staging whisper.cpp shared libs"
        COMMAND ${CMAKE_COMMAND} -E make_directory "${SHARED_LIBS_STAGED_DIR}"
        COMMAND ${CMAKE_COMMAND} -E copy_directory
        "${CMAKE_BINARY_DIR}/lib" "${SHARED_LIBS_STAGED_DIR}"
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "${CMAKE_BINARY_DIR}/bin/*.so"
        "${SHARED_LIBS_STAGED_DIR}"
        COMMENT "Copying shared libs to ${SHARED_LIBS_STAGED_DIR}"
)
add_dependencies(arm-stt-jni-stage-shared-libraries arm-stt-jni)

find_package(OpenMP REQUIRED)
target_link_libraries(arm-stt-jni PRIVATE OpenMP::OpenMP_CXX)