package com.tekome.vcman.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreFormatTest {
    @Test
    fun formatScore_dotSeparator() {
        assertEquals("7.5", formatScore(7.5, "."))
    }

    @Test
    fun formatScore_usesGivenDecimalSeparator() {
        assertEquals("7,5", formatScore(7.5, ","))
        assertEquals("0,3", formatScore(0.26, ","))
        assertEquals("-0,3", formatScore(-0.26, ","))
    }

    @Test
    fun formatScore_wholeNumbersHaveNoSeparator() {
        assertEquals("8", formatScore(8.0, ","))
        assertEquals("8", formatScore(8.0, "."))
    }

    @Test
    fun formatScore_nonFiniteIsDash() {
        assertEquals("-", formatScore(Double.NaN, ","))
        assertEquals("-", formatScore(Double.POSITIVE_INFINITY, "."))
    }
}
