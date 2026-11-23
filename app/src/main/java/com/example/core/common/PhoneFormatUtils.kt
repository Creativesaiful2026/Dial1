package com.example.core.common

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object PhoneFormatUtils {
    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    fun defaultRegion(overrideCode: String = ""): String {
        if (overrideCode.isNotBlank()) return overrideCode.uppercase(Locale.US)
        val country = Locale.getDefault().country
        return if (country.length == 2) country.uppercase(Locale.US) else "US"
    }

    fun formatDisplay(rawNumber: String, regionOverride: String = ""): String {
        if (rawNumber.isBlank()) return ""
        val trimmed = rawNumber.trim()
        if (trimmed.startsWith("*") || trimmed.startsWith("#")) return trimmed
        val region = defaultRegion(regionOverride)
        return try {
            val parsed = phoneUtil.parse(trimmed, region)
            val format = if (trimmed.startsWith("+")) {
                PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
            } else {
                PhoneNumberUtil.PhoneNumberFormat.NATIONAL
            }
            phoneUtil.format(parsed, format)
        } catch (_: NumberParseException) {
            // Partial number formatting while typing
            val formatter = phoneUtil.getAsYouTypeFormatter(region)
            var out = trimmed
            for (ch in trimmed) {
                if (ch in '0'..'9' || ch == '+') {
                    out = formatter.inputDigit(ch)
                }
            }
            out
        }
    }

    fun toE164OrNormalized(rawNumber: String, regionOverride: String = ""): String {
        val cleaned = T9Utils.normalizeDigitsOnly(rawNumber)
        if (cleaned.isBlank()) return ""
        val region = defaultRegion(regionOverride)
        return try {
            val parsed = phoneUtil.parse(cleaned, region)
            if (phoneUtil.isValidNumber(parsed)) {
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                cleaned
            }
        } catch (_: NumberParseException) {
            cleaned
        }
    }

    fun extractPhoneCandidateFromClipboard(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        val raw = text.toString().trim()
        if (raw.length > 40) return null
        val digits = raw.count { it in '0'..'9' }
        if (digits < 5 || digits > 16) return null
        val allowedChars = raw.all { it in '0'..'9' || it in setOf('+', '-', ' ', '(', ')', '.') }
        return if (allowedChars) T9Utils.normalizeDigitsOnly(raw) else null
    }
}
