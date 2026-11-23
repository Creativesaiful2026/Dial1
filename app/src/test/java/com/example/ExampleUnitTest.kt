package com.example

import com.example.core.common.PhoneFormatUtils
import com.example.core.common.T9Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun t9_conversion_maps_letters_accurately() {
        assertEquals("5646", T9Utils.toCompactT9Digits("John"))
        assertEquals("254", T9Utils.toCompactT9Digits("Ali"))
    }

    @Test
    fun t9_scoring_matches_name_prefix_initials_and_number() {
        val byName = T9Utils.scoreMatch("564", "John Doe", "5646363", "+15551234567")
        val byInitials = T9Utils.scoreMatch("53", "John Doe", "5646363", "+15551234567")
        val byNumber = T9Utils.scoreMatch("5551", "John Doe", "5646363", "+15551234567")
        val miss = T9Utils.scoreMatch("999", "John Doe", "5646363", "+15551234567")

        assertTrue(byName > 0)
        assertTrue(byInitials > 0)
        assertTrue(byNumber > 0)
        assertEquals(-1, miss)
    }

    @Test
    fun clipboard_candidate_extracts_valid_numbers_and_rejects_prose() {
        assertEquals("+8801711223344", PhoneFormatUtils.extractPhoneCandidateFromClipboard("+880 1711-223344"))
        assertNull(PhoneFormatUtils.extractPhoneCandidateFromClipboard("Hello world meeting at 5pm"))
        assertNotNull(PhoneFormatUtils.extractPhoneCandidateFromClipboard("(555) 234-5678"))
    }
}
