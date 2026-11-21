package com.tekome.vcman.data

import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class FirecrawlErrorsTest {
    @Test
    fun nonHttpFailuresAreUnavailableWithoutStatus() {
        assertEquals(WebSearchFailure.Unavailable(null), firecrawlFailureOf(IOException("offline")))
        assertEquals(WebSearchFailure.Unavailable(null), firecrawlFailureOf(IllegalStateException("boom")))
    }
}
