package com.example.domain.util

import java.util.Locale

object ArabicTextNormalizer {
    // Regex matching Arabic diacritics (Tashkeel) and Tatweel (Kashida)
    private val TASHKEEL_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /**
     * Normalizes Arabic text for search by:
     * 1. Stripping all diacritics (Tashkeel), Quranic stop marks, and Kashida.
     * 2. Unifying Hamza variants (أ, إ, آ, ٱ -> ا) and (ؤ -> و) and (ئ -> ي).
     * 3. Unifying Taa Marbuta (ة -> ه) and Alef Maqsura (ى -> ي).
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val withoutTashkeel = input.replace(TASHKEEL_REGEX, "")
        val sb = StringBuilder(withoutTashkeel.length)
        for (ch in withoutTashkeel) {
            val normalizedChar = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                else -> ch
            }
            sb.append(normalizedChar)
        }
        return sb.toString().lowercase(Locale.ROOT).trim()
    }

    fun matchesQuery(text: String, query: String): Boolean {
        if (query.isBlank()) return true
        val normText = normalize(text)
        val normQuery = normalize(query)
        return normText.contains(normQuery)
    }
}

object NumeralFormatter {
    private val ARABIC_INDIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun format(value: String, useArabicIndic: Boolean): String {
        if (!useArabicIndic) return toWestern(value)
        val sb = StringBuilder(value.length)
        for (ch in value) {
            if (ch in '0'..'9') {
                sb.append(ARABIC_INDIC_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun format(number: Int, useArabicIndic: Boolean): String =
        format(number.toString(), useArabicIndic)

    fun format(number: Long, useArabicIndic: Boolean): String =
        format(number.toString(), useArabicIndic)

    fun toWestern(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            val index = ARABIC_INDIC_DIGITS.indexOf(ch)
            if (index >= 0) {
                sb.append(('0'.code + index).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
