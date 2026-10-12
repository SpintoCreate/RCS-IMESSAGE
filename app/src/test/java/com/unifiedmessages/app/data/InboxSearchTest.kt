package com.unifiedmessages.app.data

import com.unifiedmessages.app.model.MessageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InboxSearchTest {
    @Test
    fun `same named contact remains separate across transports`() {
        val matches = InboxSearch.filter(SampleConversationRepository.conversations, "Alex", null)
        assertEquals(2, matches.size)
        assertEquals(setOf(MessageSource.IMESSAGE, MessageSource.RCS), matches.map { it.source }.toSet())
        assertNotEquals(matches[0].id, matches[1].id)
    }

    @Test
    fun `search and source filter work together`() {
        val matches = InboxSearch.filter(SampleConversationRepository.conversations, "café", MessageSource.RCS)
        assertEquals(listOf("sample-rcs-alex"), matches.map { it.id })
    }
}
