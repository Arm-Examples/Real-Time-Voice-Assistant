/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException

class DownloadErrorMessagesTest {
    @Test
    fun secureConnectionFailureUsesFriendlyMessage() {
        val message = userFacingDownloadErrorMessage(
            SSLPeerUnverifiedException(
                "Hostname huggingface.co not verified: certificate: sha256/test"
            )
        )

        assertEquals(
            "We couldn't make a secure connection to the model download server. " +
                "Check your network, VPN, proxy, or certificate settings, then try again.",
            message
        )
        assertFalse(message.contains("sha256"))
        assertFalse(message.contains("huggingface.co not verified"))
    }

    @Test
    fun availabilityCheckHidesCertificateDetails() {
        val message = userFacingModelAvailabilityErrorMessage(
            IllegalStateException(
                "Couldn't resolve model metadata",
                SSLPeerUnverifiedException(
                    "Hostname huggingface.co not verified: certificate: sha256/test"
                )
            )
        )

        assertEquals(
            "We couldn't make a secure connection to the model download server. " +
                "Check your network, VPN, proxy, or certificate settings, then try again.",
            message
        )
        assertFalse(message.contains("sha256"))
    }

    @Test
    fun nestedNetworkFailureUsesFriendlyMessage() {
        val message = userFacingDownloadErrorMessage(
            IllegalStateException("Download failed", UnknownHostException("huggingface.co"))
        )

        assertEquals(
            "We couldn't reach the model download server. " +
                "Check your internet connection, VPN, or proxy settings, then try again.",
            message
        )
    }

    @Test
    fun nonNetworkFailureKeepsSpecificMessage() {
        val message = userFacingDownloadErrorMessage(
            IllegalStateException("SHA-256 mismatch for model.gguf")
        )

        assertEquals("SHA-256 mismatch for model.gguf", message)
    }
}
