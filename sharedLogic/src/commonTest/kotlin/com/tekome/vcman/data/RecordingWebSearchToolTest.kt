package com.tekome.vcman.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordingWebSearchToolTest {
    @Test
    fun successfulSearchRecordsNothing() =
        runTest {
            val results = listOf(WebSearchResult("A", "https://a.example", "s"))
            val tool = RecordingWebSearchTool { results }

            assertEquals(results, tool.search("q"))
            assertFalse(tool.skipped)
            assertNull(tool.rejection)
        }

    @Test
    fun unavailableSearchIsRecordedAsSkippedAndRethrown() =
        runTest {
            val tool = RecordingWebSearchTool { throw WebSearchUnavailableException(429) }

            assertFailsWith<WebSearchUnavailableException> { tool.search("q") }

            assertTrue(tool.skipped)
            assertNull(tool.rejection)
        }

    @Test
    fun rejectedKeyIsRecordedAndRethrown() =
        runTest {
            val rejected = AnalysisException(AnalysisError.WebSearchRejected(401))
            val tool = RecordingWebSearchTool { throw rejected }

            assertSame(rejected, assertFailsWith<AnalysisException> { tool.search("q") })

            assertSame(rejected, tool.rejection)
            assertFalse(tool.skipped)
        }

    @Test
    fun otherFailuresAreRethrownWithoutBeingRecorded() =
        runTest {
            val tool = RecordingWebSearchTool { throw AnalysisException(AnalysisError.Network) }

            assertFailsWith<AnalysisException> { tool.search("q") }

            assertNull(tool.rejection)
            assertFalse(tool.skipped)
        }
}
