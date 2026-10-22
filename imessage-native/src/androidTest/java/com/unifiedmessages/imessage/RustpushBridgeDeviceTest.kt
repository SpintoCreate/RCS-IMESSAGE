package com.unifiedmessages.imessage

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an arm64 Android device; no pairing code or Apple account is needed. */
@RunWith(AndroidJUnit4::class)
class RustpushBridgeDeviceTest {
    @Test
    fun loadsPinnedNativeEngine() {
        assertEquals("rustpush-a7fab473-abi1", RustpushBridge().engineVersion())
    }
}
