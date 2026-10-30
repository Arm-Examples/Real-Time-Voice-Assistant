/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes the remote model files declared in the application manifest.
 *
 * @param schema manifest schema version
 * @param models model groups that the application can download and verify
 */
@Serializable
data class Manifest(
    val schema: Int = 1,
    val models: List<RemoteModelSpec> = emptyList()
)

/**
 * Describes a model group and common artifact metadata.
 *
 * @param name human readable model name
 * @param purpose description of why the model is included
 * @param framework optional backend selector. Use `common`, `all`, `*`, or omit for all backends
 * @param destination path prefix for downloaded artifacts relative to the application download directory
 * @param artifacts files that belong to this model group
 * @param repoId Hugging Face repository identifier used by artifacts that do not override it
 * @param revision immutable Hugging Face repository commit SHA used by artifacts that do not override it
 */
@Serializable
data class RemoteModelSpec(
    val name: String? = null,
    val purpose: String? = null,
    val framework: String? = null,
    val destination: String? = null,
    val artifacts: List<RemoteArtifactSpec> = emptyList(),
    val url: String? = null,
    @SerialName("repo_id") val repoId: String? = null,
    val filename: String? = null,
    val revision: String? = null
)

/**
 * Describes a remote artifact and its local download destination.
 *
 * @param path path of the file inside the remote repository
 * @param filename legacy remote filename. Prefer `path` for new manifest entries
 * @param url legacy full download URL. Prefer `repoId`, `revision`, and `path` for new manifest entries
 * @param destination path of the downloaded file relative to the model destination
 * @param sha256 optional expected SHA-256 digest for the downloaded file
 * @param repoId Hugging Face repository identifier, used when it differs from the model group
 * @param revision immutable Hugging Face repository commit SHA, used when it differs from the model group
 */
@Serializable
data class RemoteArtifactSpec(
    val path: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val destination: String? = null,
    val sha256: String? = null,
    @SerialName("repo_id") val repoId: String? = null,
    val revision: String? = null
)

/**
 * Returns true when this manifest entry should be used for the selected LLM backend.
 *
 * Model groups with a missing or blank framework are treated as shared resources for backward
 * compatibility.
 */
internal fun RemoteModelSpec.appliesToFramework(activeFramework: String): Boolean {
    val frameworkNames = framework
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    if (frameworkNames.isEmpty()) {
        return true
    }

    val selectedFramework = activeFramework.trim()
    return frameworkNames.any {
        it.equals(selectedFramework, ignoreCase = true) ||
            it.equals("common", ignoreCase = true) ||
            it.equals("all", ignoreCase = true) ||
            it == "*"
    }
}

/**
 * Result of checking whether all manifest-declared models are present and valid.
 *
 * @param available true when every declared model file exists and passes validation
 * @param missingFileNames all missing or invalid model files, when unavailable
 * @param errorMessage reason availability could not be checked or resolved
 */
data class ModelAvailabilityStatus(
    val available: Boolean,
    val missingFileNames: List<String> = emptyList(),
    val errorMessage: String? = null
)
