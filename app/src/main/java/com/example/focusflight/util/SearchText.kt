package com.example.focusflight.util

import java.text.Normalizer
import java.util.Locale

/**
 * Folds free text into the form every airport search compares on: lowercase, accents stripped,
 * punctuation collapsed to single spaces. "Düsseldorf", "São Paulo" and "Zürich" become
 * "dusseldorf", "sao paulo" and "zurich", so the spelling a pilot actually types on a phone
 * keyboard finds them.
 *
 * SQLite's `LIKE` only folds ASCII case, which is why search no longer happens in SQL - see
 * `AirportRouteSqliteDataSource.searchAirports`. Both sides of every comparison go through this
 * one function, so the airport data and the query can never be normalised differently.
 *
 * NFD decomposition handles every letter that is a base letter plus a combining mark. A handful
 * of Latin letters are distinct code points with no decomposition (ø, ł, ß, æ, ...), so those are
 * mapped by hand.
 */
fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    val sb = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
        when (ch) {
            'ø' -> sb.append('o')
            'ł' -> sb.append('l')
            'đ', 'ð' -> sb.append('d')
            'ı' -> sb.append('i')
            'ß' -> sb.append("ss")
            'æ' -> sb.append("ae")
            'œ' -> sb.append("oe")
            'þ' -> sb.append("th")
            else -> sb.append(if (ch.isLetterOrDigit()) ch else ' ')
        }
    }
    return sb.toString().trim().replace(MULTIPLE_SPACES, " ")
}

private val MULTIPLE_SPACES = Regex(" {2,}")

/** True if [query] (already normalised) starts [text] or any word inside it. */
fun startsAnyWord(text: String, query: String): Boolean =
    text.startsWith(query) || text.contains(" $query")
