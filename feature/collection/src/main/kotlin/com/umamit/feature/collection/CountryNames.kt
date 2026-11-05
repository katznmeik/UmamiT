package com.umamit.feature.collection

import java.util.IllformedLocaleException
import java.util.Locale

/**
 * The German name of the country [code] (ISO 3166-1 alpha-2), e.g. „Japan" for `JP`; a value that is no code (a name
 * typed earlier) or has no name stays as it is (CLAUDE.md rule 4).
 */
internal fun countryName(code: String): String = try {
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.GERMAN).ifEmpty { code }
} catch (_: IllformedLocaleException) {
    code
}
