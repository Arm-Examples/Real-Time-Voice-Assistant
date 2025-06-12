/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.subscribers

import android.util.Log
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.Utils.responseComplete
import com.arm.voiceassistant.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Flow
import java.util.concurrent.Flow.Subscriber

/**
 * A subscriber that listens for streaming assistant responses.
 * @param mainViewModel The view model to notify with new or complete responses
 */
class ResponseSubscriber(mainViewModel: MainViewModel) : Subscriber<String> {
    private var subscription: Flow.Subscription? = null
    private var subscribedViewModel = mainViewModel
    private var coroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * Called when the subscription is established.
     * @param subscription The Flow.Subscription instance controlling request and cancellation.
     */
    override fun onSubscribe(subscription: Flow.Subscription?) {
        this.subscription = subscription
        Log.d(VOICE_ASSISTANT_TAG, "Subscribed")
        subscription!!.request(Long.MAX_VALUE)
    }

    /**
     * Called when an error occurs during streaming.
     * @param throwable The error encountered during the stream, if available.
     */
    override fun onError(throwable: Throwable?) {
        Log.d(VOICE_ASSISTANT_TAG, "Error")
    }


    /**
     * Called when the response stream completes.
     */
    override fun onComplete() {
        Log.d(VOICE_ASSISTANT_TAG, "Response Complete!")
        subscribedViewModel.updateToIdleState()

    }

    /**
     * Called for each item (partial or complete token) in the response stream.
     * @param item The latest piece of response text emitted by the LLM stream.
     */
    override fun onNext(item: String?) {
        if (item!=null) {
            coroutineScope.launch {
                if (!responseComplete(item)) {
                    subscribedViewModel.updateResponseFieldCallback(item)
                }
                subscribedViewModel.generatedResponseCallback(item)
            }
        }

        Log.d(VOICE_ASSISTANT_TAG, "Next item - $item")
    }

    /**
     * Cancels the response subscription and coroutine scope.
     */
    fun cancel() {
        subscription?.cancel()
        coroutineScope.cancel()
    }
}
