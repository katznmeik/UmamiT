package com.umamit.feature.collection

import com.umamit.core.common.AppLanguage
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * The name of the country [code] (ISO 3166-1 alpha-2) in the app's language (GEN-10), e.g. „Japan" for `JP`,
 * „Südkorea" or „South Korea" for `KR`; a value that is no code (a name typed earlier) or has no name stays as it is.
 * Display only: the tea keeps the code.
 */
internal fun countryName(code: String): String = try {
    Locale.Builder().setRegion(code).build().getDisplayCountry(AppLanguage.current().locale).ifEmpty { code }
} catch (_: IllformedLocaleException) {
    code
}
