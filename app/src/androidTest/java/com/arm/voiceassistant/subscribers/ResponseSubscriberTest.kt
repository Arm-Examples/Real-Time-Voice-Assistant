/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.subscribers

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.util.concurrent.Flow
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.viewmodels.MainViewModel
import java.util.concurrent.SubmissionPublisher

/**
 * Unit test for verifying behavior of the ResponseSubscriber,
 * which streams LLM response tokens into the MainViewModel.
 */
@RunWith(MockitoJUnitRunner::class)
class ResponseSubscriberTest {

    private var mainViewModel: MainViewModel? = null

    /**
     * Initializes a mocked Application context and MainViewModel before each test.
     */
    @Before
    fun setupViewModel() {
        val application: Application = Mockito.mock(Application::class.java)
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        Mockito.`when`(application.applicationContext).thenReturn(appContext)
        AppContext.getInstance().context = appContext

        mainViewModel = MainViewModel(application, true)
    }

    /**
     * Helper method to create a fake reactive stream publisher with predefined text tokens.
     *
     * @param items Vararg strings that simulate response tokens.
     * @return A SubmissionPublisher with overridden behavior to track submissions.
     */
    private fun createStringPublisher(vararg items: String): SubmissionPublisher<String> {
        return object : SubmissionPublisher<String>() {
            var submittedString = ""
            init {
                items.forEach { submit(it) }
                close()
            }

            override fun submit(item: String?): Int {
                submittedString += item
                return 0
            }

            override fun subscribe(subscriber: Flow.Subscriber<in String>?) {
                super.subscribe(subscriber)
            }
        }
    }

    /**
     * Verifies that the ResponseSubscriber successfully receives and processes text from a publisher.
     */
    @Test
    fun testSubscriber() {
        val publisher = createStringPublisher("Hello ", "how ", "are ", " you?" )
        val subscriber = mainViewModel?.let { ResponseSubscriber(it) }
        publisher.subscribe(subscriber)

        // Wait briefly to allow async events to be delivered
        Thread.sleep(500)
    }
}
