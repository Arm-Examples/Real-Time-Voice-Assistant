/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for the SpeechSynthesis class.
 * Verifies Android Text-to-Speech (TTS) integration and behavior
 * including start, cancellation, and progressive token-based synthesis.
 */
@RunWith(AndroidJUnit4::class)
class SpeechSynthesisTest {
    private var speechSynthesis: SpeechSynthesis? = null
    private var context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var latch: CountDownLatch
    private val initTimeoutMs = 2000L
    private val utteranceTimeoutSeconds = 5L

    /**
     * Initializes SpeechSynthesis before each test and waits for TTS engine initialization.
     */
    @Before
    fun setup() {
        speechSynthesis = SpeechSynthesis()
        speechSynthesis?.setContext(context)
        speechSynthesis?.initSpeechSynthesis()
        awaitTtsInitialization()
        resetLatch()
    }

    /**
     * Cleans up the SpeechSynthesis instance after each test.
     */
    @After
    fun tearDown() {
        context = null
        speechSynthesis = null
    }

    /**
     * Reset countdown latch and wire it to Android TTS wrapper's test-only callback countdown
     */
    private fun resetLatch() {
        latch = CountDownLatch(1)
        AndroidTTS.getInstance(context).setOnUtteranceDoneCallback { latch.countDown() }
    }

    /**
     * Poll speech synthesis initialized for up to a specified timeout time
     */
    private fun awaitTtsInitialization() {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < initTimeoutMs) {
            if (speechSynthesis?.speechSynthesisInitialized() == true) {
                return
            }
            Thread.sleep(50)
        }
    }

    /**
     * Verifies that the TTS engine is successfully initialized.
     */
    @Test
    fun testSpeechSynthesisInitialization() {
        val initialized = speechSynthesis?.speechSynthesisInitialized()
        assertEquals(true, initialized)
    }

    /**
     * Ensures that calling startSpeechSynthesis correctly marks synthesis as in progress.
     */
    @Test
    fun testSpeechSynthesisStarted(): Unit = runBlocking {
        speechSynthesis?.startSpeechSynthesis()
        assert(speechSynthesis?.speechSynthesisInProgress() == true)
    }

    /**
     * Validates that speech generation can be cancelled and restarted without errors.
     */
    @Test
    fun testGenerateSpeechCancellation(): Unit = runBlocking {
        speechSynthesis?.startSpeechSynthesis()
        resetLatch()
        speechSynthesis?.generateSpeech("First sentence answer to a question. ")
        assert(speechSynthesis?.speechSynthesisInProgress() == true)
        latch.await(utteranceTimeoutSeconds, TimeUnit.SECONDS)
        speechSynthesis?.cancelSpeechSynthesis()

        speechSynthesis?.startSpeechSynthesis()
        resetLatch()
        speechSynthesis?.generateSpeech("Speaking again after cancel")
        assert(speechSynthesis?.speechSynthesisInProgress() == true)
        latch.await(utteranceTimeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Tests that speech synthesis handles sentence boundaries appropriately,
     * generating output only when a sentence is complete.
     */
    @Test
    fun testSpeechSynthesisGenerationOnPeriodBreakOnly(): Unit = runBlocking {
        val tokens = "First sentence answer to a generation question.".split(" ")
        speechSynthesis?.startSpeechSynthesis()
        resetLatch()
        for(token in tokens) {
            speechSynthesis?.addWordsToSpeechSynthesis(token)
        }

        latch.await(utteranceTimeoutSeconds, TimeUnit.SECONDS)
        speechSynthesis?.addWordsToSpeechSynthesis(" Another")
        assert(speechSynthesis?.speechSynthesisInProgress() == true)

        resetLatch()
        speechSynthesis?.addWordsToSpeechSynthesis(" sentence. Third")
        assert(speechSynthesis?.speechSynthesisInProgress() == true)
        latch.await(utteranceTimeoutSeconds, TimeUnit.SECONDS)
    }
}
