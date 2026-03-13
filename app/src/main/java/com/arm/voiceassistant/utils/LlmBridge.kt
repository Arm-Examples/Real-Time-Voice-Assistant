/*
 * SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arm.voiceassistant.utils

import kotlinx.coroutines.CancellableContinuation


import android.util.Log
import com.arm.Llm
import java.util.concurrent.atomic.AtomicBoolean


private const val RESULT_OK = 0
private const val RESULT_CANCELLED = 1
private const val RESULT_ERROR = 2

sealed class NativeResult {
    data class Success(val data: String?) : NativeResult()
    data class Error(val code: Int, val message: String?) : NativeResult()
    data object Cancelled : NativeResult()
}

object NativeBridge {

    @JvmStatic fun onNativeComplete(operationId: Long, resultCode: Int, payload: String?) {
        Log.d("NativeBridge", "Got completion callback")
        ContinuationRegistry.complete(operationId, resultCode, payload)
    }
}

data class RegisterItem(
    val cont: CancellableContinuation<NativeResult>,
    val llmBridge: LlmBridge
)

// Holds suspended continuations waiting for native completion.
object ContinuationRegistry {

    private val nextId = java.util.concurrent.atomic.AtomicLong(1L)
    private val map = java.util.concurrent.ConcurrentHashMap<Long, RegisterItem>()

    fun register(cont: CancellableContinuation<NativeResult>, llmBridge: LlmBridge): Long {
        val id = nextId.getAndIncrement()

        val item = RegisterItem(cont, llmBridge)
        map[id] = item
        return id
    }

    fun complete(operationId: Long, resultCode: Int, payload: String?) {

        Log.d("NativeBridge", "ContinuationRegistry Got completion callback $resultCode payload $payload")

        val item = map.remove(operationId) ?: return
        val result = if (resultCode == 0) {
            NativeResult.Success(payload)
        } else if (resultCode == 1) {
            NativeResult.Cancelled
        } else {
            NativeResult.Error(resultCode, payload)
        }
        item.llmBridge.operationId = null
        item.cont.resume(result) {}
    }

    fun cancel(operationId: Long) {

        Log.d("NativeBridge", "ContinuationRegistry Got cancel callback")

        map.remove(operationId)?.cont?.cancel()
    }
}

open class LlmBridge(var llm: Llm) {

    external fun nativeCancel(operationId: Long)

    var operationId: Long? = null

    var isCancelInProgress =  AtomicBoolean(false)

    suspend fun getNextToken(): NativeResult = kotlinx.coroutines.suspendCancellableCoroutine { cont ->

        if (this.operationId != null) {
            cont.resumeWith(
                Result.success(
                    NativeResult.Error(code = 2, message = "Operation in progress")
                )
            )
            return@suspendCancellableCoroutine
        }

        val operationId = ContinuationRegistry.register(cont, this)
        this.operationId = operationId

        cont.invokeOnCancellation {
            ContinuationRegistry.cancel(operationId)
            this.operationId = null
        }

        val token = llm.getNextTokenCancellable(operationId)

        ContinuationRegistry.complete(operationId, 0, token)
    }

    fun cancel() {
        isCancelInProgress.set(true)
        operationId?.let { operationId ->
            llm.cancel(operationId)

            this.operationId = null
        }
    }

    fun clearCancel() {
        isCancelInProgress.set(false)
    }

}