package com.umamit.buildlogic

/**
 * Maps the app version from `gradle.properties` (`umamit.version`) to the
 * Android versionCode (06 §6):
 *
 * `versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + N`
 *
 * where N is
 * - the release-candidate number for `X.Y.Z-rc.N` (1–98),
 * - 99 for a final release `X.Y.Z`,
 * - 1 for a probe release `X.Y.Z-probe` (D-040).
 *
 * MINOR and PATCH must be below 100 so that every later version has a higher
 * versionCode. Anything else is rejected with an [IllegalArgumentException].
 */
fun versionCodeOf(version: String): Int {
    val match = VERSION_PATTERN.matchEntire(version)
        ?: throw IllegalArgumentException(
            "Invalid umamit.version '$version': expected X.Y.Z, X.Y.Z-rc.N (N 1–98) or X.Y.Z-probe",
        )
    val (majorText, minorText, patchText, suffix, rcText) = match.destructured
    val major = majorText.toLong()
    val minor = minorText.toLong()
    val patch = patchText.toLong()
    require(minor <= MAX_MINOR_OR_PATCH) { "Invalid umamit.version '$version': MINOR must be 0–99" }
    require(patch <= MAX_MINOR_OR_PATCH) { "Invalid umamit.version '$version': PATCH must be 0–99" }

    val n = when {
        suffix.isEmpty() -> FINAL_RELEASE
        suffix == "probe" -> PROBE
        else -> {
            val rc = rcText.toLong()
            require(rc in RC_RANGE) { "Invalid umamit.version '$version': rc number must be 1–98" }
            rc
        }
    }

    val code = major * 1_000_000 + minor * 10_000 + patch * 100 + n
    require(code <= MAX_VERSION_CODE) { "Invalid umamit.version '$version': versionCode $code is too large" }
    return code.toInt()
}

// Numbers without leading zeros (length-capped so they fit a Long); the ranges
// are checked separately for clear error messages.
private val VERSION_PATTERN =
    Regex("""(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})(?:-(probe|rc\.(0|[1-9]\d{0,8})))?""")

private const val MAX_MINOR_OR_PATCH = 99L
private const val FINAL_RELEASE = 99L
private const val PROBE = 1L
private val RC_RANGE = 1L..98L

/** Largest versionCode Android accepts. */
private const val MAX_VERSION_CODE = 2_100_000_000L
