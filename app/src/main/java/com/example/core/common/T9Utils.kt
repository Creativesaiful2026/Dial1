package com.example.core.common

import java.util.Locale

object T9Utils {
    private val charToDigit = mapOf(
        'A' to '2', 'B' to '2', 'C' to '2',
        'D' to '3', 'E' to '3', 'F' to '3',
        'G' to '4', 'H' to '4', 'I' to '4',
        'J' to '5', 'K' to '5', 'L' to '5',
        'M' to '6', 'N' to '6', 'O' to '6',
        'P' to '7', 'Q' to '7', 'R' to '7', 'S' to '7',
        'T' to '8', 'U' to '8', 'V' to '8',
        'W' to '9', 'X' to '9', 'Y' to '9', 'Z' to '9'
    )

    fun toT9Digits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.uppercase(Locale.US)) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                charToDigit.containsKey(ch) -> sb.append(charToDigit[ch])
                ch == ' ' || ch == '-' || ch == '.' -> sb.append(' ')
            }
        }
        return sb.toString()
    }

    fun toCompactT9Digits(text: String): String =
        toT9Digits(text).replace(" ", "")

    fun normalizeDigitsOnly(number: String): String {
        val sb = StringBuilder(number.length)
        for (i in number.indices) {
            val c = number[i]
            if (c in '0'..'9') sb.append(c)
            else if (c == '+' && sb.isEmpty()) sb.append(c)
        }
        return sb.toString()
    }

    fun digitsOnly(number: String): String = number.filter { it in '0'..'9' }

    /**
     * Scores a contact against a numeric T9 query.
     * Returns -1 if no match, or a positive score (higher = stronger match).
     */
    fun scoreMatch(
        queryDigits: String,
        displayName: String,
        compactT9Name: String,
        normalizedNumber: String
    ): Int {
        if (queryDigits.isBlank()) return -1
        val cleanQuery = digitsOnly(queryDigits)
        if (cleanQuery.isEmpty()) return -1

        val numberDigits = digitsOnly(normalizedNumber)
        var score = -1

        // 1. Direct phone number prefix/contains match
        val numIdx = numberDigits.indexOf(cleanQuery)
        if (numIdx == 0) {
            score = 1000 - numberDigits.length
        } else if (numIdx > 0) {
            score = maxOf(score, 700 - numIdx)
        }

        // 2. Word-start T9 match in display name (e.g. "56" matches "John" or "J... O...")
        val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wordT9s = words.map { toCompactT9Digits(it) }

        // Check initials + prefix of each word
        for ((wIdx, wT9) in wordT9s.withIndex()) {
            if (wT9.startsWith(cleanQuery)) {
                val bonus = if (wIdx == 0) 950 else 850
                score = maxOf(score, bonus - wT9.length)
            }
        }

        // Check sequential initials across words (e.g. "53" -> "John Doe")
        if (wordT9s.size >= 2) {
            val initials = wordT9s.mapNotNull { it.firstOrNull() }.joinToString("")
            if (initials.startsWith(cleanQuery)) {
                score = maxOf(score, 880)
            }
        }

        // 3. Substring T9 match anywhere in compact name
        val t9Idx = compactT9Name.indexOf(cleanQuery)
        if (t9Idx >= 0) {
            score = maxOf(score, 600 - t9Idx)
        }

        return score
    }
}
