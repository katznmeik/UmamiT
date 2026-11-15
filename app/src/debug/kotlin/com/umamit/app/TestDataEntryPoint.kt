package com.umamit.app

import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.VesselRepository
import com.umamit.core.timer.controller.SavedSheetRequests
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The app's own stores for code outside Hilt's injection, like [SettingsEntryPoint]: the instrumented
 * transition test (TransitionJankTest, NFR-PERF-12, D-117) seeds a tea and a finished session through
 * them and asks for the „Gespeichert" sheet of that session. Nothing in the app calls it, so it is in the debug
 * source set only (the emulator job runs the debug build): the release exposes no test seam.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestDataEntryPoint {
    fun teas(): TeaRepository

    fun recipes(): RecipeRepository

    fun vessels(): VesselRepository

    fun stock(): StockRepository

    fun sessions(): SessionRepository

    fun savedSheets(): SavedSheetRequests
}
