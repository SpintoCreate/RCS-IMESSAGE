package com.unifiedmessages.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchingTest {
    @Test fun formattedUsQueryMatchesBothStoredNumberForms() {
        val terms = SearchMatching.numberTerms("+1 (555) 123-4567")
        assertEquals("15551234567", terms.digits)
        assertEquals("5551234567", terms.digitsWithoutUsPrefix)
        assertTrue("+1 (555) 123-4567".digits().contains(terms.digits))
        assertTrue("555-123-4567".digits().contains(terms.digitsWithoutUsPrefix))
    }

    @Test fun partialNumberFindsFormattedStoredAddress() {
        val terms = SearchMatching.numberTerms("5551234")
        assertEquals("5551234", terms.digits)
        assertTrue("+1 (555) 123-4567".digits().contains(terms.digits))
        assertFalse("+1 (555) 987-6543".digits().contains(terms.digits))
    }

    @Test fun namesDoNotBecomeSpuriousNumberQueries() {
        assertEquals("", SearchMatching.numberTerms("Alex 1").digits)
        assertEquals("", SearchMatching.numberTerms("Alex 1").digitsWithoutUsPrefix)
    }

    @Test fun messageSnippetShowsContextAroundADeepMatch() {
        val body = "A long introduction that would fill the entire preview before the relevant package tracking update arrives."
        val snippet = SearchMatching.messageSnippet(body, "package tracking")!!
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.contains("package tracking"))
        assertTrue(snippet.length < body.length)
    }

    @Test fun messageSnippetIgnoresCaseAndRejectsUnrelatedText() {
        assertEquals("Your Package arrived", SearchMatching.messageSnippet("Your Package arrived", "package"))
        assertNull(SearchMatching.messageSnippet("Your Package arrived", "meeting"))
    }

    private fun String.digits(): String = filter(Char::isDigit)
}
