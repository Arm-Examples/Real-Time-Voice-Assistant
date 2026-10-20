/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.huggingface

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_CONNECTION_TIMEOUT
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_HEADERS_JSON
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_HOST
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.ToastService
import com.arm.voiceassistant.utils.userFacingDownloadErrorMessage
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * HuggingFace API service.
 *
 * Uses REST API for query and download of models from HuggingFace host.
 */
interface RestModelsApiService {

    /**
     * List available models with specific model tag and maximum parameters
     * @param context application context
     * @param framework framework type to list models for
     */
    suspend fun listModels(context: Context,
                           framework: String): Result<List<HuggingFaceModel>>

    /**
     * Download selected HuggingFace model's file via DownloadManager
     * @param context application context
     * @param client HTTP client to use for download
     * @param modelInfo hugging face model info
     * @param downloadPath path on the device to download to
     * @param spec model download spec
     * @param listener progress listener
     */
    suspend fun downloadModelFile(
        context: Context,
        client: OkHttpClient,
        modelInfo: HuggingFaceModel,
        downloadPath: String,
        spec: ModelDownloadSpec,
        listener: ProgressListener? = null,
        onCallCreated: ((Call) -> Unit)? = null
    ): Result<File>
}

/**
 * Hugging Face model class
 */
data class HuggingFaceModel(
    val id: String,
    val modelId: String,
    val filename: String,
    val revision: String?
)

private val HUGGING_FACE_HEADERS = JSONObject(HUGGING_FACE_HEADERS_JSON)

private fun applyHuggingFaceHeaders(connection: HttpURLConnection) {
    val keys = HUGGING_FACE_HEADERS.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        connection.setRequestProperty(key, HUGGING_FACE_HEADERS.getString(key))
    }
}

interface ProgressListener {
    fun onProgress(downloadedBytes: Long, totalBytes: Long?)
}

sealed class DownloadProgressEvent {
    data class Start(val fileName: String) : DownloadProgressEvent()
    data class Progress(val downloadedBytes: Long, val totalBytes: Long?) : DownloadProgressEvent()
    data class Complete(val fileName: String) : DownloadProgressEvent()
    data class Error(val fileName: String, val error: Throwable) : DownloadProgressEvent()
}

/**
 * Logging progress listener
 * Adds logging on the device to show percentage downloaded
 */
class LoggingProgressListener(
    private val tag: String = VOICE_ASSISTANT_TAG,
    private val onEvent: ((DownloadProgressEvent) -> Unit)? = null
) : ProgressListener {
    private var lastLoggedPercent: Int = -10
    private var loggedUnknownTotal: Boolean = false

    fun onStart(fileName: String) {
        lastLoggedPercent = -10
        loggedUnknownTotal = false
        Log.d(tag, "Model download started: $fileName")
        onEvent?.invoke(DownloadProgressEvent.Start(fileName))
    }

    fun onComplete(fileName: String) {
        Log.d(tag, "Model download completed: $fileName")
        ToastService.showToast("Model download completed: $fileName")
        onEvent?.invoke(DownloadProgressEvent.Complete(fileName))
    }

    fun onError(fileName: String, error: Throwable) {
        Log.e(tag, "Model download failed: $fileName", error)
        ToastService.showToast("Model download failed: $fileName. ${userFacingDownloadErrorMessage(error)}")
        onEvent?.invoke(DownloadProgressEvent.Error(fileName, error))
    }

    override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
        onEvent?.invoke(DownloadProgressEvent.Progress(downloadedBytes, totalBytes))
        if (totalBytes == null || totalBytes <= 0L) {
            if (!loggedUnknownTotal) {
                loggedUnknownTotal = true
                Log.d(tag, "Model download progress: $downloadedBytes bytes")
            }
            return
        }

        val pct = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        if (pct >= lastLoggedPercent + 10) {
            val snapped = (pct / 10) * 10
            lastLoggedPercent = snapped
            Log.d(tag, "Model download progress: $downloadedBytes/$totalBytes ($snapped%)")
        }
    }
}

/**
 * Resolved Hugging Face source metadata used for SHA-256 metadata lookup.
 */
data class HuggingFaceSource(
    val repository: String,
    val revision: String,
    val path: String
) {
    init {
        require(repository.isNotBlank()) { "Hugging Face repository is required" }
        require(revision.isNotBlank()) { "Hugging Face revision is required" }
        require(path.isNotBlank()) { "Hugging Face source path is required" }
    }
}

/**
 * Resolved model download request.
 *
 * Manifest-backed instances contain values flattened from model defaults, artifact overrides, and
 * legacy Hugging Face URLs so the downloader does not depend on raw manifest schema.
 */
data class ModelDownloadSpec(
    val url: String,
    val fileName: String,
    val destination: String,
    val sha256: String? = null,
    val bearerToken: String? = null,
    val huggingFaceSource: HuggingFaceSource? = null
)

private fun String?.normalizedOrNull(): String? =
    this?.trim()?.ifBlank { null }

/*
 * HuggingFace Remote Data Source
 */
class HuggingFaceApiService : RestModelsApiService {
    private data class VerificationKey(
        val path: String,
        val length: Long,
        val lastModified: Long,
        val expectedSha256: String?
    )

    companion object {
        private val verifiedFiles = mutableSetOf<VerificationKey>()
    }

    private var downloadJob: Job? = null
    private var downloadClient: OkHttpClient? = null
    private var downloadCall: Call? = null
    @Volatile
    private var downloadCanceled = false

    internal suspend fun resolveSha256IfNeeded(
        client: OkHttpClient,
        spec: ModelDownloadSpec
    ): Result<ModelDownloadSpec> = withContext(Dispatchers.IO) {
        val configuredSha256 = spec.sha256.normalizedOrNull()
        if (configuredSha256 != null) {
            return@withContext Result.success(spec.copy(sha256 = configuredSha256))
        }

        val source = spec.huggingFaceSource
        if (source == null) {
            return@withContext downloadFailure(
                "Cannot resolve SHA-256 from Hugging Face metadata for ${spec.errorContext()}"
            ).map { spec }
        }

        runCatching {
            spec.copy(
                sha256 = resolveHuggingFaceSha256(
                    client,
                    spec,
                    source.repository,
                    source.revision,
                    source.path
                )
            )
        }
    }

    fun startModelDownload(
        scope: CoroutineScope,
        context: Context,
        modelInfo: HuggingFaceModel,
        downloadPath: String,
        spec: ModelDownloadSpec,
        listener: LoggingProgressListener? = null
    ) {
        downloadCanceled = false
        val client = OkHttpClient()
        downloadClient = client
        downloadJob = scope.launch {
            val guardedListener = listener?.let { wrapped ->
                object : ProgressListener {
                    override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
                        if (!downloadCanceled) {
                            wrapped.onProgress(downloadedBytes, totalBytes)
                        }
                    }
                }
            }
            listener?.onStart(spec.fileName)
            val result = downloadModelFile(
                context = context,
                client = client,
                modelInfo = modelInfo,
                downloadPath = downloadPath,
                spec = spec,
                listener = guardedListener,
                onCallCreated = { call ->
                    downloadCall = call
                }
            )
            result.onSuccess {
                if (!downloadCanceled) {
                    listener?.onComplete(spec.fileName)
                }
            }.onFailure { e ->
                if (!downloadCanceled) {
                    listener?.onError(spec.fileName, e)
                }
            }.also {
                downloadClient = null
                downloadJob = null
                downloadCall = null
            }
        }
    }

    fun cancelModelDownload(scope: CoroutineScope) {
        downloadCanceled = true
        downloadJob?.cancel()
        downloadJob = null
        downloadCall?.cancel()
        downloadCall = null
        val client = downloadClient
        downloadClient = null
        if (client != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    client.dispatcher.cancelAll()
                    client.connectionPool.evictAll()
                }.onFailure { error ->
                    Log.w(VOICE_ASSISTANT_TAG, "Failed to cancel download client", error)
                }
            }
        }
    }

    override suspend fun listModels(
        context: Context,
        framework: String,
    ): Result<List<HuggingFaceModel>> = withContext(Dispatchers.IO) {
        try {
            val queryParams = loadHfModelsQuery(context, framework)

            val urlBuilder = Uri.parse("${HUGGING_FACE_HOST}api/models").buildUpon()
            val keys = queryParams.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                urlBuilder.appendQueryParameter(key, queryParams.optString(key))
            }

            val url = URL(urlBuilder.build().toString())
            Log.i(VOICE_ASSISTANT_TAG, "HF url: $url")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HUGGING_FACE_CONNECTION_TIMEOUT
                readTimeout = HUGGING_FACE_CONNECTION_TIMEOUT
                applyHuggingFaceHeaders(this)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(
                    IllegalStateException("HuggingFace API error ($responseCode): $errorBody")
                )
            }

            val contentType = connection.contentType ?: ""
            val body = connection.inputStream.bufferedReader().use { it.readText() }

            if (!contentType.contains("application/json", ignoreCase = true)) {
                val snippet = body.take(200)
                return@withContext Result.failure(
                    IllegalStateException("Unexpected content type ($contentType): $snippet")
                )
            }

            val reader = JsonReader(StringReader(body)).apply { isLenient = true }
            val jsonArray = JsonParser.parseReader(reader).asJsonArray

            val models = jsonArray.mapNotNull { element ->
                val obj = element.asJsonObject

                val fullModelId = when {
                    obj.has("modelId") && !obj.get("modelId").isJsonNull ->
                        obj.get("modelId").asString

                    obj.has("id") && !obj.get("id").isJsonNull ->
                        obj.get("id").asString

                    else -> return@mapNotNull null
                }

                val parts = fullModelId.split("/", limit = 2)
                val owner = parts.getOrElse(0) { "" }
                val repo = parts.getOrElse(1) { fullModelId }

                HuggingFaceModel(
                    id = owner,
                    modelId = repo,
                    filename = "",
                    revision = null
                )
            }


            Log.i(
                VOICE_ASSISTANT_TAG,
                "HF models found: ${models.size} -> ${models.joinToString { "${it.id}/${it.modelId}" }}"
            )

            Result.success(models)
        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Failed to list models", e)
            Result.failure(e)
        }
    }

    private fun loadHfModelsQuery(context: Context, framework: String): JSONObject {
        val json = context.assets.open("models_query.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        return root.optJSONObject(framework)?.optJSONObject("query")
            ?: root.optJSONObject("default")?.optJSONObject("query")
            ?: JSONObject()
    }

    private fun resolveHuggingFaceSha256(
        client: OkHttpClient,
        spec: ModelDownloadSpec,
        repository: String,
        revision: String,
        sourcePath: String
    ): String {
        val url = huggingFacePathsInfoUrl(repository, revision)
        val body = JSONObject()
            .put("paths", JSONArray().put(sourcePath))
            .put("expand", true)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
        spec.bearerToken.normalizedOrNull()?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Hugging Face checksum lookup failed for ${spec.fileName} (HTTP ${response.code})"
                )
            }
            val responseBody = response.body?.string().orEmpty()
            val entries = try {
                JSONArray(responseBody)
            } catch (_: JSONException) {
                throw IllegalStateException(
                    "Hugging Face returned invalid checksum metadata for ${spec.fileName}"
                )
            }
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val path = entry.optString("path")
                    .ifBlank { entry.optString("rfilename") }
                if (path != sourcePath) continue

                val sha256 = entry.optJSONObject("lfs")?.let { lfs ->
                    lfs.optString("sha256")
                        .ifBlank { lfs.optString("oid") }
                        .ifBlank { lfs.optString("file_oid") }
                } ?: entry.optString("sha256")

                val normalized = sha256.trim().lowercase()
                if (isSha256Hex(normalized)) {
                    return@use normalized
                }
            }

            throw IllegalStateException(
                "Hugging Face did not provide a SHA-256 checksum for ${spec.fileName}"
            )
        }
    }

    private fun huggingFacePathsInfoUrl(repository: String, revision: String): String {
        val builder = Uri.parse(HUGGING_FACE_HOST).buildUpon()
            .appendPath("api")
            .appendPath("models")
        repository.split('/').filter { it.isNotBlank() }.forEach { segment ->
            builder.appendPath(segment)
        }
        return builder
            .appendPath("paths-info")
            .appendPath(revision)
            .build()
            .toString()
    }

    override suspend fun downloadModelFile(
        context: Context,
        client: OkHttpClient,
        modelInfo: HuggingFaceModel,
        downloadPath: String,
        spec: ModelDownloadSpec,
        listener: ProgressListener?,
        onCallCreated: ((Call) -> Unit)?
    ): Result<File> = withContext(Dispatchers.IO) {
        if (spec.fileName.isBlank()) {
            return@withContext downloadFailure("Model filename is required")
        }
        if (spec.url.isBlank()) {
            return@withContext downloadFailure("Model download URL is required")
        }

        try {
            val dir = File(downloadPath).apply { mkdirs() }
            val finalFile = resolveDownloadTarget(dir, spec)
            val partFile = File(finalFile.parentFile ?: dir, "${finalFile.name}.part")
            finalFile.parentFile?.mkdirs()

            if (finalFile.exists() && verifyIfNeeded(finalFile, spec.sha256)) {
                return@withContext Result.success(finalFile)
            }

            val legacyFile = File(dir, spec.fileName)
            if (legacyFile.exists() && verifyIfNeeded(legacyFile, spec.sha256)) {
                if (finalFile.exists() && !finalFile.delete()) {
                    return@withContext downloadFailure(
                        "Failed to replace existing model file: ${finalFile.absolutePath}"
                    )
                }
                if (!legacyFile.renameTo(finalFile)) {
                    return@withContext downloadFailure(
                        "Failed to move legacy model file to ${finalFile.absolutePath}"
                    )
                }
                markVerified(finalFile, spec.sha256)
                return@withContext Result.success(finalFile)
            }

            if (!partFile.exists()) {
                val legacyPartFile = File(dir, "${spec.fileName}.part")
                if (legacyPartFile.exists()) {
                    legacyPartFile.renameTo(partFile)
                }
            }

            var downloaded = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder()
                .url(spec.url)

            if (downloaded > 0L) {
                requestBuilder.header("Range", "bytes=$downloaded-")
            }
            spec.bearerToken.normalizedOrNull()?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }

            val call = client.newCall(requestBuilder.build())
            onCallCreated?.invoke(call)
            call.execute().use { response ->
                when (response.code) {
                    206, 200 -> {
                        if (response.code == 200 && downloaded > 0L) {
                            partFile.delete()
                            downloaded = 0L
                        }

                        val body = response.body
                            ?: return@withContext downloadFailure("Empty response body")
                        val total = computeTotalBytes(response, downloaded)

                        partFile.parentFile?.mkdirs()
                        FileOutputStream(partFile, downloaded > 0L && response.code == 206).use { out ->
                            val input = body.byteStream()
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read: Int
                            var current = downloaded

                            while (input.read(buffer).also { read = it } != -1) {
                                coroutineContext.ensureActive()
                                out.write(buffer, 0, read)
                                current += read
                                listener?.onProgress(current, total)
                            }
                            out.fd.sync()
                        }
                    }

                    416 -> {
                        if (!partFile.exists()) {
                            return@withContext downloadFailure(
                                "Range not satisfiable and no partial file present"
                            )
                        }
                    }

                    else -> {
                        return@withContext downloadFailure(
                            "HTTP ${response.code} while downloading ${spec.errorContext()}"
                        )
                    }
                }
            }

            if (!verifyIfNeeded(partFile, spec.sha256)) {
                partFile.delete()
                return@withContext downloadFailure("SHA-256 mismatch for ${spec.errorContext()}")
            }

            if (finalFile.exists() && !finalFile.delete()) {
                return@withContext downloadFailure(
                    "Failed to replace existing model file: ${finalFile.absolutePath}"
                )
            }
            if (!partFile.renameTo(finalFile)) {
                return@withContext downloadFailure(
                    "Failed to promote partial download to ${finalFile.absolutePath}"
                )
            }
            markVerified(finalFile, spec.sha256)

            Result.success(finalFile)
        } catch (error: CancellationException) {
            Log.e(VOICE_ASSISTANT_TAG, "Download cancelled for ${spec.errorContext()}", error)
            Result.failure(error)
        } catch (exception: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Failed to download ${spec.errorContext()}", exception)
            Result.failure(exception)
        }
    }

    private fun downloadFailure(message: String): Result<File> {
        val exception = IllegalStateException(message)
        Log.e(VOICE_ASSISTANT_TAG, message, exception)
        return Result.failure(exception)
    }

    private fun computeTotalBytes(response: Response, alreadyDownloaded: Long): Long? {
        val cr = response.header("Content-Range")
        if (cr != null) {
            val total = cr.substringAfter('/').toLongOrNull()
            if (total != null) return total
        }
        val cl = response.body?.contentLength()?.takeIf { it >= 0 } ?: return null
        return if (response.code == 206) alreadyDownloaded + cl else cl
    }

    internal fun verifyIfNeeded(file: File, expectedSha256: String?): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val key = verificationKey(file, expectedSha256)
        synchronized(verifiedFiles) {
            if (key in verifiedFiles) return true
        }

        val isValid = expectedSha256 == null || sha256(file).equals(expectedSha256, ignoreCase = true)
        if (isValid) markVerified(file, expectedSha256)
        return isValid
    }

    private fun markVerified(file: File, expectedSha256: String?) {
        val key = verificationKey(file, expectedSha256)
        synchronized(verifiedFiles) {
            verifiedFiles.removeAll { it.path == key.path }
            verifiedFiles.add(key)
        }
    }

    private fun verificationKey(file: File, expectedSha256: String?) = VerificationKey(
        path = file.absolutePath,
        length = file.length(),
        lastModified = file.lastModified(),
        expectedSha256 = expectedSha256?.lowercase()
    )

    private fun isSha256Hex(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun ModelDownloadSpec.errorContext(): String {
        val file = huggingFaceSource?.path.normalizedOrNull()
            ?: fileName.normalizedOrNull()
            ?: "<unknown>"
        val repo = huggingFaceSource?.repository.normalizedOrNull()
        val pinnedRevision = huggingFaceSource?.revision.normalizedOrNull()
        if (repo == null && pinnedRevision == null) {
            return file
        }
        return "repository=${repo ?: "<unknown>"} revision=${pinnedRevision ?: "<unknown>"} file=$file"
    }

    private fun resolveDownloadTarget(downloadRoot: File, spec: ModelDownloadSpec): File {
        val destination = spec.destination.trim().trimStart('/', '\\')
        if (destination.isBlank()) {
            return File(downloadRoot, spec.fileName)
        }
        return if (destination.endsWith("/") || destination.endsWith(File.separator)) {
            File(File(downloadRoot, destination), spec.fileName)
        } else {
            File(downloadRoot, destination)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
