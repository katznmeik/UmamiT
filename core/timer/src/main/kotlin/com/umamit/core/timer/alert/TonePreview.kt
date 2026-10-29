package com.umamit.core.timer.alert

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * *Anhören* in the tone picker (SET-09) and the *Vorwarnton* picker (SET-26): the sound once, so
 * the PO hears the sound itself; unlike *Alarm testen* (SET-31) no tick, no vibration and no alert
 * length, and [stop] ends it at once.
 */
interface TonePreview {
    /** Plays the tone stored under [toneKey] once (an unknown key: the Klangschale), replacing a preview. */
    fun play(toneKey: String)

    /** Plays the *Vorwarnton* stored under [preAlertKey] once (an unknown key: the default), replacing a preview. */
    fun playPreAlert(preAlertKey: String)

    /** Stops a playing preview; idempotent. */
    fun stop()
}

/**
 * The preview with the alert's own sound output and rule (04 §9.1): it sounds only where the
 * alert would sound (ringer normal, no call, not blocked by Do Not Disturb), with the same usage
 * and audio focus. The settings close it before an alert can play (rule 8), and a real alert's
 * player stops nothing of it, so the screen stops it when the picker closes.
 */
class RingerTonePreview internal constructor(
    private val environment: AlertEnvironment,
    private val sound: AlertSoundOutput,
    private val scheduler: AlertScheduler,
) : TonePreview {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        environment = AndroidAlertEnvironment(context.applicationContext),
        sound = MediaPlayerSoundOutput(context.applicationContext),
        scheduler = HandlerAlertScheduler(),
    )

    override fun play(toneKey: String) = playOnce(AlertSound.tone(toneKey))

    override fun playPreAlert(preAlertKey: String) = playOnce(PreAlertSound.of(preAlertKey).sound)

    private fun playOnce(alertSound: AlertSound) {
        stop()
        if (!AlertMatrix.outputs(environment.conditions(), vibrationEnabled = false).sound) return
        if (!sound.requestFocus(onLost = ::stop)) return
        sound.play(alertSound)
        scheduler.schedule(alertSound.lengthMs) { sound.abandonFocus() }
    }

    override fun stop() {
        scheduler.cancelAll()
        sound.stop()
        sound.abandonFocus()
    }
}

/** Binds the device's [TonePreview] for the settings (SCR-25). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TonePreviewModule {
    @Binds
    abstract fun tonePreview(impl: RingerTonePreview): TonePreview
}
