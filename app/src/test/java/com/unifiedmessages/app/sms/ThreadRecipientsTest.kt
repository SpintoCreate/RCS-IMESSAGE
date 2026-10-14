package com.unifiedmessages.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadRecipientsTest {
    @Test fun formattingOrderAndDuplicateChipsDoNotChangeIdentity() {
        val normalized = ThreadRecipients.requireNormalized(
            listOf("(555) 123-4567", "+44 20 7946 0018", "+1 555-123-4567"), "US",
        )
        assertEquals(setOf("+15551234567", "+442079460018"), normalized)
        assertTrue(ThreadRecipients.sameRecipients(normalized, listOf("+44 20 7946 0018", "5551234567"), "US"))
    }

    @Test fun threadLookupRequiresExactlyTheSameParticipants() {
        val candidates = listOf(
            ThreadRecipients.Candidate("one", listOf("+1 555 123 4567")),
            ThreadRecipients.Candidate("group", listOf("+44 20 7946 0018", "+1 (555) 123-4567")),
        )
        assertEquals("group", ThreadRecipients.findExactThreadId(
            listOf("5551234567", "+442079460018"), "US", candidates,
        ))
        assertEquals("one", ThreadRecipients.findExactThreadId(listOf("5551234567"), "US", candidates))
        assertNull(ThreadRecipients.findExactThreadId(listOf("5551234567", "+15559876543"), "US", candidates))
    }

    @Test fun invalidOrMissingRecipientsDoNotMatchAThread() {
        val candidates = listOf(ThreadRecipients.Candidate("short-code", listOf("12345")))
        assertNull(ThreadRecipients.findExactThreadId(listOf("12345"), "US", candidates))
        assertNull(ThreadRecipients.findExactThreadId(emptyList(), "US", candidates))
    }

    @Test fun unmatchedRecipientsResolveToOneCanonicalThreadCreationSet() {
        val resolution = ThreadRecipients.resolve(
            listOf("(555) 123-4567", "+1 555 123 4567"), "US", emptyList(),
        )
        assertNull(resolution.existingThreadId)
        assertEquals(setOf("+15551234567"), resolution.normalizedRecipients)
    }
}
