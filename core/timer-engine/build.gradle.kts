plugins {
    alias(libs.plugins.umamit.jvm.library)
}

dependencies {
    // The engine's public API uses the shared enums (StepKind, TimerPhase) and
    // the clock reading, so both are part of its API (06 §3: pure modules only).
    api(project(":core:model"))
    api(project(":core:common"))
}

// 06 §7 / spec #27 seam 1: at least 90 % line coverage of the engine (`koverVerify`).
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}
