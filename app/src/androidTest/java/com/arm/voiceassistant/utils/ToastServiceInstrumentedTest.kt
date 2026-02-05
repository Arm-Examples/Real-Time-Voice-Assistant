/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.utils.ToastService.testInterceptor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToastServiceInstrumentedTest {

    private lateinit var context: Context
    private val seen = mutableListOf<String>()

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        AppContext.getInstance().context = context
        ToastService.initialize(context)
        testInterceptor = { msg -> seen.add(msg) }
    }

    @After
    fun cleanup() {
        ToastService.shutdown()
        testInterceptor = null
        seen.clear()
    }

    @Test
    fun toastService_showsInOrder_andRejectsBlanks() {
        ToastService.showToast("") // should be ignored
        ToastService.showToast("Toast A")
        ToastService.showToast("Toast B")
        Thread.sleep(1200)
        ToastService.showToast("Toast C")
        Thread.sleep(2500)

        assertEquals(listOf("Toast A", "Toast B", "Toast C"), seen)
    }

    @Test
    fun toastService_suppressesDuplicateToasts() {
        ToastService.showToast("Toast X")
        ToastService.showToast("Toast X") // duplicate
        Thread.sleep(2500)
        assertEquals(1, seen.size)
        assertEquals("Toast X", seen[0])
    }
}


