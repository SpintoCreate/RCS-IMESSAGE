package com.unifiedmessages.app.matrix

import com.unifiedmessages.app.provider.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatrixBridgeDetectorTest {
    private val bot = "@bridge:example.org"
    private val content = """{"bridgebot":"$bot","protocol":{"id":"gmessages"},"channel":{"id":"+15551234567"}}"""

    @Test fun verifiesBothBridgeStateFormats() {
        MatrixBridgeDetector.stateEventTypes.forEach { type ->
            val result = MatrixBridgeDetector.detect(type, "\$event", bot, content, bot)
            assertEquals(NetworkType.GOOGLE_MESSAGES, result?.networkType)
            assertEquals("+15551234567", result?.remoteChannelId)
        }
    }

    @Test fun rejectsUnconfiguredOrForgedBridgeEvents() {
        assertNull(MatrixBridgeDetector.detect("m.bridge", "\$event", bot, content, null))
        assertNull(MatrixBridgeDetector.detect("m.bridge", "\$event", "@attacker:example.org", content, bot))
        assertNull(MatrixBridgeDetector.detect("m.bridge", "\$event", bot,
            content.replace(bot, "@attacker:example.org"), bot))
        assertNull(MatrixBridgeDetector.detect("m.room.message", "\$event", bot, content, bot))
    }

    @Test fun unknownProtocolRemainsMatrix() {
        val unknown = content.replace("gmessages", "unrecognized")
        assertEquals(NetworkType.MATRIX,
            MatrixBridgeDetector.detect("uk.half-shot.bridge", "\$event", bot, unknown, bot)?.networkType)
    }
}
