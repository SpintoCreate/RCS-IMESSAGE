package com.unifiedmessages.app.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatrixMediaRefTest {
    @Test fun encryptedSourceRoundTripsExactly() {
        val original = MatrixMediaRef("matrix:@me:matrix.org", """{"url":"mxc://matrix.org/x","key":"å\u0000:+/=💙"}""")
        assertEquals(original, MatrixMediaRef.parse(original.encode()))
    }

    @Test fun otherUrisDoNotParseAsMatrixMedia() {
        assertNull(MatrixMediaRef.parse("content://sms/12"))
    }
}
