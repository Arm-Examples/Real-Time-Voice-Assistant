<!--
    SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>

    SPDX-License-Identifier: Apache-2.0
-->

# Android Voice Assistant Application

<!-- TOC -->
* [Android Voice Assistant Application](#android-voice-assistant-application)
  * [Introduction](#introduction)
  * [Pre-requisites](#pre-requisites)
  * [Dependencies](#dependencies)
  * [Application pipeline](#application-pipeline)
    * [Speech to Text Library](#speech-to-text-library)
    * [Large Language Models Library](#large-language-models-library)
        * [Visual Question Answering (Optional)](#visual-question-answering-optional)
    * [Text to Speech Component](#text-to-speech-component)
  * [KleidiAI Configuration](#kleidiai-configuration)
  * [LLM Framework](#llm-framework)
  * [Custom LLM Configuration](#custom-llm-configuration)
  * [Custom STT Configuration](#custom-stt-configuration)
  * [Resources](#resources)
  * [Tested Devices](#tested-devices)
  * [Supported ABIs](#supported-abis)
  * [Supported NDK Versions](#supported-ndk-versions)
  * [Troubleshooting](#troubleshooting)
  * [Known issues](#known-issues)
  * [Contributions](#contributions)
  * [Trademarks](#trademarks)
  * [License](#license)
<!-- TOC -->

---

## Introduction

This is an example Android application featuring an integrated voice assistant.
It utilizes Speech-to-Text (STT) and Large Language Models (LLM) to process user voice commands, convert them into text,
and generate intelligent responses. Android Text-to-Speech API is then used to produce a voice response.
This application demonstrates a complete voice interaction pipeline for Android and by default uses [KleidiAI library](https://gitlab.arm.com/kleidi/kleidiai)
for optimized performance on Arm® CPU.

## Pre-requisites

1. Download and install the latest version of [Android Studio](https://developer.android.com/studio)
2. Install the [Android NDK](https://developer.android.com/studio/projects/install-ndk). This project was tested with Android NDK r29.
3. Python 3 must be installed. It is used to push resources and model files to the device.

## Dependencies

This application is dependent on two modules which are downloaded during the build:

* STT - speech to text module which is used to transform user's audio prompt into a text representation
  * all required build configurations are located in the [stt](stt) directory
  * whisper.cpp is used for this module
  * the git repository and revision of downloaded module can be seen in [CMakeLists.txt](stt/CMakeLists.txt)
  * specific build flags and build variants for this module can be seen in [build.gradle.kts](stt/build.gradle.kts)
* LLM - large language models module which is used for prompt answering part of the pipeline
  * all needed build configurations inside [llm](llm) directory
  * llama.cpp is used for this module
  * the git repository and revision of downloaded module can be seen in [CMakeLists.txt](llm/CMakeLists.txt)
  * specific build flags and build variants for this module can be seen in [build.gradle.kts](llm/build.gradle.kts)


The modules are downloaded into the `stt/` and `llm/` directories and built as Android libraries.

>**NOTE**: The modules require cmake version 3.27 or above.
>
>Download the needed cmake version.
>Create a `local.properties` file in the root directory of the repository and specify the CMake path as follows:
>```
>cmake.dir=<location-of-cmake-install>
>```

## Application pipeline

This application contains three parts which can be explained in more detail.

### Speech to Text Library

Speech-to-Text is also known as Automatic Speech Recognition.
This part of the pipeline focuses on converting spoken language into written text.
Speech recognition is done in the following stages:
* device the microphone captures spoken language as an audio waveform
* audio waveform is broken into small timeframes, features are extracted from it to represent sound
* neural network is used to predict the most likely transcription of audio based on grammar and context
* final recognized text is generated and used for the next stage of the pipeline

### Large Language Models Library

Large Language Models (LLMs) are designed for natural language understanding, and in this application,
they are used for question-answering.  The text transcription from previous part
of the pipeline is used as an input to the neural model. During initialization,
the application assigns a persona to the LLM to ensure a friendly and informative voice assistant experience.
By default, the application uses asynchronous flow for this part of the pipeline, meaning that parts of response
are collected as they become available. The application UI is updated with each new token
and these are also used for final stage of the pipeline.

##### Visual Question Answering (Optional)
The application includes support for Visual Question Answering (VQA), enabling users to provide an image as input and subsequently query the model with natural language questions grounded in that visual context.
To initiate VQA, the image must be uploaded prior to starting the voice recording. Upon upload, the image undergoes encoding via the integrated vision encoder, producing a set of visual embeddings.
These embeddings are retained in the chat context until the context is explicitly reset, allowing for multi-turn interaction and follow-up queries based on the same image.

### Text to Speech Component

Currently, this part of the application pipeline is using Android Text-to-Speech API
with some extra functionality in the application to ensure smooth and natural speech output.
In synchronous mode, speech is only generated after the full response from LLM is received.
By default, the application operates in asynchronous mode, where speech synthesis starts as soon as a sufficient portion
of the response (such as a half or full sentence) is available.
Any additional responses are queued for processing by the Android Text-to-Speech engine.

## KleidiAI Configuration

The default KleidiAI configuration is ABI-specific:
* arm64-v8a: KleidiAI is enabled by default.
* x86_64: KleidiAI is disabled by default.

To override these defaults, simply adjust the build flag:
* To disable KleidiAI, use `-PkleidiAI=false`.
* To enable KleidiAI on an ABI where it is disabled by default, use `-PkleidiAI=true`.

## LLM Framework

The application supports multiple LLM backend frameworks. You can choose the desired backend at build
time using the `llmFramework` Gradle property.

Available options:
* `llama.cpp` (default)
* `onnxruntime-genai`
* `mnn`
* `mediapipe`

You can specify the framework when building the app from the command line:
> ./gradlew assembleRelease -PllmFramework=onnxruntime-genai

If no value is provided, the default is used.
> **NOTE**: The default value is defined in [gradle properties](gradle.properties) and can be modified
> if a different framework is preferred by default.

> **NOTE**: MediaPipe™ support is only for limited models. Please set your [Hugging Face](https://huggingface.co/) access token in your `.netrc` file.
> Refer to the [custom configuration of mediapipe](https://github.com/Arm-Examples/LLM-Runner#mediapipe-model) for more details.

Details on supported LLM models can be found [here](https://github.com/Arm-Examples/LLM-Runner#supported-models).

## Custom LLM Configuration

This application supports custom configuration of the LLM via a JSON-formatted file located here: `app/src/model_configuration_files/{LLM Framework Name}{Text or Vision}ConfigUser.json`.

Details on custom LLM configuration can be found in the links below:

[Custom configuration of llama.cpp](https://github.com/Arm-Examples/LLM-Runner#llama-cpp-model)

[Custom configuration of onnxruntime-genai](https://github.com/Arm-Examples/LLM-Runner#onnxruntime-genai-model)

[Custom configuration of mnn](https://github.com/Arm-Examples/LLM-Runner#mnn-model)

[Custom configuration of mediapipe](https://github.com/Arm-Examples/LLM-Runner#mediapipe-model)


## Custom STT Configuration

In addition to the default settings, this application allows you to provide custom configuration parameters for the STT via a JSON-formatted file named `app/src/model_configuration_files/whisperConfig.json`. This file must contain the following mandatory keys:
* `printRealtime`: Show live partial results on the console.
* `printProgress`: Display a progress bar while decoding.
* `printTimeStamps`: Add timecodes in front of each segment.
* `printSpecial`: Print special tokens such as <|nospeech|>.
* `translate`: Translate everything into English instead of transcribing.
* `language`: ISO code of the spoken language, or "auto" for detection.
* `numThreads`: Number of CPU threads Whisper may use.
* `offsetMs`: Skip this many milliseconds at the start of the audio.
* `noContext`: Don’t feed previous text back as context.
* `singleSegment`: Stop after the first (≈30 s) segment.

You only need to modify the values associated with these keys if you wish to customize the STT's behavior. Do not remove any of the keys, as they are mandatory for the configuration to work properly.

## Resources

The STT and LLM modules automatically download the required neural network models during the build process.
These models are then deployed to the device for processing voice commands and generating responses with a [push resources script](app/pushAppResources.py)

## Tested Devices

This application has been tested on Google Pixel 8 Pro (Android 14) to ensure compatibility and performance.

## Supported ABIs

The following ABIs (Application Binary Interfaces) are supported:

* arm64-v8a
* x86_64

## Supported NDK Versions

The application has been built and tested using the following Android NDK r29 version.
Other versions may work but have not been officially tested.

## Troubleshooting

* Ensure that the correct CMake version is installed and configured in local.properties file.
* Verify that dependencies are correctly downloaded by checking the logs in Android Studio.
* If facing memory issues, try increasing heap size in [gradle.properties](gradle.properties).
* If the execution of the app is very slow, check the build variant used :
  * debug build will not show good performance but useful for debugging and tests
  * release build should be used for best performance

## Known issues

> **NOTE**: The cancellation flow of the application is currently under testing. Further updates and improvements will follow.

## Contributions

The Real Time Voice Assistant project welcomes contributions. For more details on contributing to the repo please see the [contributors guide](./contributing.md#contributions).

## Trademarks

* Arm® and KleidiAI™ are registered trademarks or trademarks of Arm® Limited (or its subsidiaries) in the US and/or
  elsewhere.
* MediaPipe™ and Android™ are trademarks of Google LLC.

## License

This project is distributed under the software licenses in [LICENSES](LICENSES) directory.

This project also includes a number of other projects, please see the license sections below for additional details:


[Arm-Examples / LLM-Runner](https://github.com/Arm-Examples/LLM-Runner#license)

[Arm-Examples / STT-Runner](https://github.com/Arm-Examples/STT-Runner#license)

