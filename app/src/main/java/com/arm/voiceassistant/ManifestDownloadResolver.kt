/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import com.arm.voiceassistant.huggingface.HuggingFaceSource
import com.arm.voiceassistant.huggingface.ModelDownloadSpec
import com.arm.voiceassistant.utils.Constants
import java.net.URI

private val HUGGING_FACE_COMMIT_SHA = Regex("^[0-9a-fA-F]{40}$")

private fun String?.normalizedOrNull(): String? =
    this?.trim()?.ifBlank { null }

internal fun resolveManifestDownloadSpecs(
    manifest: Manifest,
    activeFramework: String
): Result<List<ModelDownloadSpec>> = runCatching {
    manifest.models
        .filter { it.appliesToFramework(activeFramework) }
        .flatMap { it.resolveDownloadSpecs() }
}

internal fun isValidHuggingFaceCommitRevision(revision: String): Boolean =
    HUGGING_FACE_COMMIT_SHA.matches(revision.trim())

private fun RemoteModelSpec.resolveDownloadSpecs(): List<ModelDownloadSpec> {
    val legacyArtifact = artifacts.isEmpty() &&
        listOf(filename, url, repoId).any { it.normalizedOrNull() != null }
    val resolvedArtifacts = if (legacyArtifact) {
        listOf(
            RemoteArtifactSpec(
                filename = filename,
                url = url,
                destination = destination,
                repoId = repoId,
                revision = revision
            )
        )
    } else {
        artifacts
    }
    val modelDestination = if (legacyArtifact) "" else destination.normalizedOrNull().orEmpty()
    return resolvedArtifacts.map { artifact ->
        resolveArtifact(modelDestination, artifact)
    }
}

private fun RemoteModelSpec.resolveArtifact(
    modelDestination: String,
    artifact: RemoteArtifactSpec
): ModelDownloadSpec {
    val legacyUrl = artifact.url.normalizedOrNull() ?: url.normalizedOrNull().orEmpty()
    val parsedUrl = parseHuggingFaceResolveUrl(legacyUrl)
    val repoId = artifact.repoId.normalizedOrNull()
        ?: this.repoId.normalizedOrNull()
        ?: parsedUrl?.repoId.orEmpty()
    val revision = artifact.revision.normalizedOrNull()
        ?: this.revision.normalizedOrNull()
        ?: parsedUrl?.revision.orEmpty()
    val remotePath = (artifact.path.normalizedOrNull()
        ?: parsedUrl?.path.normalizedOrNull()
        ?: artifact.filename.normalizedOrNull()
        ?: "")
        .trimStart('/', '\\')
    val destination = artifact.resolveDestination(
        modelDestination = modelDestination,
        remotePath = remotePath
    )
    val fileName = manifestFileNameFromDestination(destination)
        .ifBlank { manifestFileNameFromDestination(artifact.localFallbackName()) }
        .ifBlank { manifestFileNameFromDestination(remotePath) }
    val errorFile = remotePath.ifBlank { fileName }

    if (repoId.isBlank()) {
        manifestValidationError(
            model = this,
            repoId = repoId,
            revision = revision,
            file = errorFile,
            reason = "Hugging Face repository id is required"
        )
    }
    if (remotePath.isBlank()) {
        manifestValidationError(
            model = this,
            repoId = repoId,
            revision = revision,
            file = errorFile,
            reason = "Hugging Face repository file path is required"
        )
    }
    if (!isValidHuggingFaceCommitRevision(revision)) {
        manifestValidationError(
            model = this,
            repoId = repoId,
            revision = revision,
            file = errorFile,
            reason = "Hugging Face revision must be a full 40-character commit SHA"
        )
    }

    return ModelDownloadSpec(
        url = "${Constants.HUGGING_FACE_HOST}$repoId/resolve/$revision/$remotePath",
        fileName = fileName,
        destination = destination.ifBlank { fileName },
        sha256 = artifact.sha256.normalizedOrNull(),
        huggingFaceSource = HuggingFaceSource(
            repository = repoId,
            revision = revision,
            path = remotePath
        )
    )
}

private fun RemoteArtifactSpec.resolveDestination(
    modelDestination: String,
    remotePath: String
): String {
    val artifactDestination = destination.normalizedOrNull().orEmpty()
    val localName = localFallbackName().ifBlank { manifestFileNameFromDestination(remotePath) }
    return when {
        modelDestination.isBlank() && artifactDestination.isBlank() -> localName
        modelDestination.isBlank() -> artifactDestination
        artifactDestination.isBlank() -> combineManifestPaths(modelDestination, localName)
        else -> combineManifestPaths(modelDestination, artifactDestination)
    }
}

private fun RemoteArtifactSpec.localFallbackName(): String =
    if (path.normalizedOrNull() == null) {
        filename.normalizedOrNull().orEmpty()
    } else {
        ""
    }

private fun combineManifestPaths(prefix: String, suffix: String): String {
    val cleanPrefix = prefix.trim().trimEnd('/', '\\')
    val cleanSuffix = suffix.trim().trimStart('/', '\\')
    return when {
        cleanPrefix.isBlank() -> cleanSuffix
        cleanSuffix.isBlank() -> cleanPrefix
        else -> "$cleanPrefix/$cleanSuffix"
    }
}

private fun manifestFileNameFromDestination(destination: String): String {
    val trimmed = destination.trim()
    if (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
        return ""
    }
    return trimmed.substringAfterLast('/').substringAfterLast('\\')
}

private fun manifestValidationError(
    model: RemoteModelSpec,
    repoId: String,
    revision: String,
    file: String,
    reason: String
): Nothing {
    val modelName = model.name.normalizedOrNull() ?: "<unnamed>"
    throw IllegalArgumentException(
        "Invalid manifest model '$modelName': $reason " +
            "(repository=${repoId.ifBlank { "<missing>" }}, " +
            "revision=${revision.ifBlank { "<missing>" }}, " +
            "file=${file.ifBlank { "<missing>" }})"
    )
}

private data class HuggingFaceResolveUrl(
    val repoId: String,
    val revision: String,
    val path: String
)

private fun parseHuggingFaceResolveUrl(url: String): HuggingFaceResolveUrl? {
    if (url.isBlank()) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (!uri.host.equals("huggingface.co", ignoreCase = true)) return null

    val segments = uri.rawPath
        ?.trim('/')
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (segments.size < 5 || segments[2] != "resolve") return null

    return HuggingFaceResolveUrl(
        repoId = "${segments[0]}/${segments[1]}",
        revision = segments[3],
        path = segments.drop(4).joinToString("/")
    )
}
