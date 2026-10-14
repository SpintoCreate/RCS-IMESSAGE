package com.unifiedmessages.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsTextReactionParserTest {
    @Test fun parsesStraightAndCurlyQuotes() {
        assertEquals(
            SmsTextReaction("hello", "👍"),
            SmsTextReactionParser.parse("Liked 'hello'"),
        )
        assertEquals(
            SmsTextReaction("See you soon!", "❤️"),
            SmsTextReactionParser.parse("Loved “See you soon!”"),
        )
    }

    @Test fun recognizesRemovalWithoutDeletingOriginalMessage() {
        assertEquals(
            SmsTextReaction("hello", "👍", isRemoval = true),
            SmsTextReactionParser.parse("Removed a like from \"hello\""),
        )
    }

    @Test fun ordinaryOrAmbiguousSmsStaysText() {
        listOf(
            "I Liked 'hello'",
            "Liked hello",
            "Liked 'hello' extra",
            "Liked 'hello\nworld'",
            "Liked '   '",
            "Liked “mismatched'",
        ).forEach { assertNull(it, SmsTextReactionParser.parse(it)) }
    }
}
