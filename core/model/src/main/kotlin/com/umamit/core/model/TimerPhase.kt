package com.umamit.core.model

/** Phase of the active brew's current step (05 §5, 04 §3.1). */
enum class TimerPhase {
    READY,
    RUNNING,
    PAUSED,
    ALERTING,
    FINISHED,
}
