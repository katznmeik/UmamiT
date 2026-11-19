package com.umamit.feature.settings.backup

/** An [AppDataWipe] that only counts its calls and answers [result]: nothing is ever deleted. */
class FakeAppDataWipe(var result: WipeResult = WipeResult.Wiped) : AppDataWipe {
    var calls = 0

    override suspend fun wipe(): WipeResult {
        calls++
        return result
    }
}
