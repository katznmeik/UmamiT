package com.umamit.core.data

/** The one-time seed photo download as the first-start setup (SCR-01, M1-15) shows it (ONB-06). */
sealed interface SeedPhotoProgress {
    /** Not started in this process (also when it already ran in an earlier one). */
    data object Idle : SeedPhotoProgress

    /** Fetching photo [current] of [total] (robots.txt is read while on photo 1). */
    data class Running(val current: Int, val total: Int) : SeedPhotoProgress

    /**
     * Finished without a stop: [stored] photos became covers. Photos whose path robots.txt
     * disallows, that the shop doesn't have (404) or that are no complete JPEG are left out.
     */
    data class Done(val stored: Int) : SeedPhotoProgress

    /** *Überspringen*: cancelled; photos stored before stay, the rest keep the placeholder. */
    data object Skipped : SeedPhotoProgress

    /** Stopped on [cause] with no further request; photos stored before stay. No error is shown. */
    data class Failed(val cause: FailureCause) : SeedPhotoProgress

    /** Why the download stopped (CLAUDE.md rule 7, 08 §2.8, D-050). */
    enum class FailureCause {
        /** Offline, DNS, timeout, a dropped connection or an oversized answer. */
        NETWORK,

        /** A Cloudflare challenge page; never bypassed. */
        CHALLENGE,

        /** A Cloudflare block page. */
        BLOCKED,

        /** HTTP 429. */
        TOO_MANY_REQUESTS,

        /** A `Retry-After` header. */
        RETRY_AFTER,

        /** HTTP 401, 403 or 5xx: the shop refuses or is down. */
        HTTP_ERROR,

        /** The photo or the run marker could not be saved (disk full, database error). */
        STORAGE,
    }
}
