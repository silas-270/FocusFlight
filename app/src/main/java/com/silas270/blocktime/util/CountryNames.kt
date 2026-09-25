package com.silas270.blocktime.util

import java.util.Locale

/**
 * "DZ" -> "Algeria".
 *
 * The airports DB stores only ISO 3166-1 alpha-2 codes ([com.silas270.blocktime.data.model.Airport.isoCountry])
 * and the app ships no code-to-name table, so every surface that showed a country previously showed
 * the bare code. A 54-row "Master of Africa" checklist of two-letter codes is unreadable, hence
 * this.
 *
 * Resolved through the platform's own ICU data rather than a hand-maintained ~250-entry map: it is
 * already on the device, it needs no Room migration, and it cannot drift out of date. Always asked
 * in [Locale.US] so the label matches the rest of the app's English copy rather than following the
 * device language for this one list.
 *
 * Falls back to the code itself for anything ICU does not recognise - a made-up or retired region
 * returns the input unchanged rather than throwing or rendering blank.
 *
 * That fallback needs two separate guards, because ICU fails in two different ways. A malformed
 * region (anything that isn't two letters) makes `Locale.Builder` throw, which is what the
 * `runCatching` is for. A *well-formed but unassigned* one does not throw at all - it resolves to
 * the literal placeholder [UNKNOWN_REGION]. Without the second guard a checklist row for an
 * unassigned code would read "Unknown Region", which is worse than showing the raw code: it hides
 * which country the row is even about.
 */
private const val UNKNOWN_REGION = "Unknown Region"

fun countryDisplayName(isoCode: String): String {
    val code = isoCode.trim().uppercase(Locale.US)
    if (code.isEmpty()) return isoCode
    val name = runCatching {
        Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.US)
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() && it != code && it != UNKNOWN_REGION } ?: code
}
