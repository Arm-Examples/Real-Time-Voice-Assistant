/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val DEFAULT_DOWNLOAD_FAILURE_MESSAGE = "Failed to download app models"
private const val DEFAULT_MODEL_CHECK_FAILURE_MESSAGE = "Couldn't check required models. Try again."

private const val SECURE_CONNECTION_FAILURE_MESSAGE =
    "We couldn't make a secure connection to the model download server. " +
        "Check your network, VPN, proxy, or certificate settings, then try again."

private const val NETWORK_FAILURE_MESSAGE =
    "We couldn't reach the model download server. " +
        "Check your internet connection, VPN, or proxy settings, then try again."

internal fun userFacingDownloadErrorMessage(error: Throwable?): String {
    return userFacingModelErrorMessage(error, DEFAULT_DOWNLOAD_FAILURE_MESSAGE)
}

internal fun userFacingModelAvailabilityErrorMessage(error: Throwable?): String {
    return userFacingModelErrorMessage(error, DEFAULT_MODEL_CHECK_FAILURE_MESSAGE)
}

private fun userFacingModelErrorMessage(error: Throwable?, defaultMessage: String): String {
    if (error == null) {
        return defaultMessage
    }

    return when {
        error.isSecureConnectionFailure() -> SECURE_CONNECTION_FAILURE_MESSAGE
        error.isNetworkFailure() -> NETWORK_FAILURE_MESSAGE
        else -> error.message?.takeIf { it.isNotBlank() } ?: defaultMessage
    }
}

private fun Throwable.isSecureConnectionFailure(): Boolean {
    if (anyCause { it is SSLException }) {
        return true
    }

    val message = lowerCaseCauseMessages()
    return listOf(
        "certificate",
        "hostname",
        "not verified",
        "ssl",
        "tls"
    ).any { it in message }
}

private fun Throwable.isNetworkFailure(): Boolean {
    if (anyCause {
            it is UnknownHostException ||
                it is SocketTimeoutException ||
                it is ConnectException ||
                it is NoRouteToHostException ||
                it is SocketException
        }
    ) {
        return true
    }

    val message = lowerCaseCauseMessages()
    return listOf(
        "unable to resolve host",
        "failed to connect",
        "connection refused",
        "connection reset",
        "network is unreachable",
        "no route to host",
        "timed out",
        "timeout"
    ).any { it in message }
}

private inline fun Throwable.anyCause(predicate: (Throwable) -> Boolean): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 8) {
        if (predicate(current)) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

private fun Throwable.lowerCaseCauseMessages(): String {
    val messages = mutableListOf<String>()
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 8) {
        current.message?.let(messages::add)
        current = current.cause
        depth++
    }
    return messages.joinToString(" ").lowercase()
}
