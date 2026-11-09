package com.umamit.core.designsystem.component

/** An answer of [ChooseTimerToEndDialog]: the quick timer, or a brew session of [teaName] (null: tea unknown). */
data class TimerChoice(val teaName: String?, val quick: Boolean)
