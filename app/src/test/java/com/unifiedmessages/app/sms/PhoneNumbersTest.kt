package com.unifiedmessages.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun nationalNumbersUseTheDeviceRegion() {
        assertEquals("+15551234567", PhoneNumbers.normalizeToE164("(555) 123-4567", "US"))
        assertEquals("+442079460018", PhoneNumbers.normalizeToE164("020 7946 0018", "GB"))
    }

    @Test fun internationalNumbersDoNotDependOnTheDeviceRegion() {
        assertEquals("+442079460018", PhoneNumbers.normalizeToE164("+44 20 7946 0018", "US"))
    }

    @Test fun shortCodesAndAlphanumericSendersCannotReceiveReplies() {
        listOf("911", "12345", "AMAZON", "1-800-FLOWERS", "*123#").forEach { sender ->
            assertFalse(sender, PhoneNumbers.isReplyableAddress(sender, "US"))
            assertNull(sender, PhoneNumbers.normalizeToE164(sender, "US"))
        }
        assertTrue(PhoneNumbers.isReplyableAddress("+1 (555) 123-4567", "US"))
    }
}
