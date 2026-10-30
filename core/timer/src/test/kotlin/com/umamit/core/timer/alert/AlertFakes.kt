package com.umamit.core.timer.alert

// Test doubles for the seams of RingerModeAlertPlayer (fakes, no mocks).

class FakeEnvironment(var conditions: AlertConditions = AlertConditions(RingerMode.NORMAL)) : AlertEnvironment {
    /** The phone's alarm volume is 0 (TMR-31). */
    var alarmVolumeOff = false

    override fun conditions(): AlertConditions = conditions

    override fun alarmVolumeOff(): Boolean = alarmVolumeOff
}

/** Records every call as text, e.g. `play KLANGSCHALE @ 3500`, with the scheduler's virtual time. */
class FakeSoundOutput(private val clock: FakeScheduler) : AlertSoundOutput {
    val events = mutableListOf<String>()
    var grantFocus = true
    var focusHeld = false
        private set
    var playing: AlertSound? = null
        private set
    private var onLost: (() -> Unit)? = null

    /**
     * The sounds of the last [prepare]: loaded. Like the real output, which loads a used sound
     * again after its cue, a play leaves them loaded.
     */
    var wanted: Set<AlertSound> = emptySet()
        private set

    /** Every [prepare] as text, e.g. `prepare [TICK, KLANGSCHALE] @ 0` (kept apart from [events]). */
    val prepares = mutableListOf<String>()

    /** The sounds a play had to load first, in order: none of them was loaded before (D-092). */
    val loadedOnPlay = mutableListOf<AlertSound>()

    override val ready: Set<AlertSound> get() = wanted

    override fun prepare(sounds: Set<AlertSound>) {
        prepares += "prepare ${sounds.toList()} @ ${clock.now}"
        wanted = sounds
    }

    override fun requestFocus(onLost: () -> Unit): Boolean {
        events += "requestFocus @ ${clock.now}"
        if (grantFocus) {
            focusHeld = true
            this.onLost = onLost
        }
        return grantFocus
    }

    override fun abandonFocus() {
        events += "abandonFocus @ ${clock.now}"
        focusHeld = false
        onLost = null
    }

    /** The gain of every play, in order, e.g. `KLANGSCHALE 0.5 @ 3500` (SET-27; kept apart from [events]). */
    val gains = mutableListOf<String>()

    override fun play(sound: AlertSound, gain: Float) {
        events += "play $sound @ ${clock.now}"
        gains += "$sound $gain @ ${clock.now}"
        if (sound !in wanted) loadedOnPlay += sound
        playing = sound
    }

    override fun stop() {
        events += "stop @ ${clock.now}"
        playing = null
    }

    /** Another app takes the audio focus. */
    fun loseFocus() {
        onLost?.invoke()
    }

    fun plays(sound: AlertSound): List<Long> =
        events.filter { it.startsWith("play $sound ") }.map { it.substringAfter("@ ").toLong() }
}

class FakeHaptics(private val clock: FakeScheduler) : AlertHaptics {
    val events = mutableListOf<String>()
    var vibrating: Haptic? = null
        private set

    override fun play(haptic: Haptic) {
        events += "play $haptic @ ${clock.now}"
        vibrating = haptic
    }

    override fun cancel() {
        events += "cancel @ ${clock.now}"
        vibrating = null
    }

    fun plays(haptic: Haptic): List<Long> =
        events.filter { it.startsWith("play $haptic ") }.map { it.substringAfter("@ ").toLong() }
}

/** Virtual-time scheduler: [advanceBy] runs the due actions in time order. */
class FakeScheduler : AlertScheduler {
    var now = 0L
        private set
    private val pending = mutableListOf<Pair<Long, () -> Unit>>()

    val pendingCount: Int get() = pending.size

    override fun schedule(delayMs: Long, action: () -> Unit) {
        pending += (now + delayMs) to action
    }

    override fun cancelAll() {
        pending.clear()
    }

    fun advanceBy(ms: Long) {
        val end = now + ms
        while (true) {
            val next = pending.filter { it.first <= end }.minByOrNull { it.first } ?: break
            pending.remove(next)
            now = next.first
            next.second()
        }
        now = end
    }
}
