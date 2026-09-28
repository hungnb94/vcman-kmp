package com.tekome.vcman.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SampleRubricsTest {
    @Test
    fun cryptoBenchScore_hasExpectedTitleAndMultilineText() {
        val sample = SampleRubrics.cryptoBenchScore

        assertEquals("Crypto Bench Score", sample.title)
        assertTrue(sample.text.isNotBlank())
        assertTrue(sample.text.lines().size > 1, "sample rubric text should be multi-line")
    }

    @Test
    fun sampleRubric_rejectsBlankTitle() {
        assertFailsWith<IllegalArgumentException> { SampleRubric(title = "  ", text = "Some text") }
    }

    @Test
    fun sampleRubric_rejectsBlankText() {
        assertFailsWith<IllegalArgumentException> { SampleRubric(title = "Title", text = "  ") }
    }
}
