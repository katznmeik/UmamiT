package com.umamit.feature.onboarding.howto

import androidx.annotation.StringRes
import com.umamit.feature.onboarding.R
import com.umamit.feature.onboarding.mockup.HelpMockupId

/** One numbered step of an Anleitung (SCR-36): its text, and the mock-up shown under it when it has one. */
class HowToStep(@StringRes val text: Int, val mockup: HelpMockupId? = null)

/**
 * The eight Anleitungen (ONB-08, SCR-35) in the list's order: the title and the one-line summary of the list, the
 * intro, the numbered steps (a step may carry a mock-up, whose marker shows the step's number) and the optional
 * „Tipp" line. The copy is word for word from 03 SCR-35/SCR-36, in `strings.xml` of both languages.
 */
enum class HowToTopic(
    @StringRes val title: Int,
    @StringRes val line: Int,
    @StringRes val intro: Int,
    val steps: List<HowToStep>,
    @StringRes val tip: Int?,
) {
    /** Topic 1 (SCR-35 row 1). */
    WithoutCatalog(
        title = R.string.howto_1_title,
        line = R.string.howto_1_line,
        intro = R.string.howto_1_intro,
        steps = listOf(
            HowToStep(R.string.howto_1_step1, HelpMockupId.MyTeasMenu),
            HowToStep(R.string.howto_1_step2, HelpMockupId.Sorte),
            HowToStep(R.string.howto_1_step3, null),
            HowToStep(R.string.howto_1_step4, HelpMockupId.TeaNew),
            HowToStep(R.string.howto_1_step5, null),
        ),
        tip = null,
    ),

    /** Topic 2 (SCR-35 row 2). */
    ShopLink(
        title = R.string.howto_2_title,
        line = R.string.howto_2_line,
        intro = R.string.howto_2_intro,
        steps = listOf(
            HowToStep(R.string.howto_2_step1, null),
            HowToStep(R.string.howto_2_step2, HelpMockupId.CatalogMenu),
            HowToStep(R.string.howto_2_step3, HelpMockupId.CatalogTea),
            HowToStep(R.string.howto_2_step4, HelpMockupId.AddSheet),
            HowToStep(R.string.howto_2_step5, null),
        ),
        tip = R.string.howto_2_tip,
    ),

    /** Topic 3 (SCR-35 row 3). */
    StockAndCost(
        title = R.string.howto_3_title,
        line = R.string.howto_3_line,
        intro = R.string.howto_3_intro,
        steps = listOf(
            HowToStep(R.string.howto_3_step1, HelpMockupId.Stock),
            HowToStep(R.string.howto_3_step2, null),
            HowToStep(R.string.howto_3_step3, null),
            HowToStep(R.string.howto_3_step4, HelpMockupId.History),
            HowToStep(R.string.howto_3_step5, null),
        ),
        tip = null,
    ),

    /** Topic 4 (SCR-35 row 4). */
    Recipes(
        title = R.string.howto_4_title,
        line = R.string.howto_4_line,
        intro = R.string.howto_4_intro,
        steps = listOf(
            HowToStep(R.string.howto_4_step1, HelpMockupId.Recipes),
            HowToStep(R.string.howto_4_step2, null),
            HowToStep(R.string.howto_4_step3, HelpMockupId.Steps),
            HowToStep(R.string.howto_4_step4, null),
            HowToStep(R.string.howto_4_step5, HelpMockupId.Receive),
        ),
        tip = R.string.howto_4_tip,
    ),

    /** Topic 5 (SCR-35 row 5). */
    Brewing(
        title = R.string.howto_5_title,
        line = R.string.howto_5_line,
        intro = R.string.howto_5_intro,
        steps = listOf(
            HowToStep(R.string.howto_5_step1, null),
            HowToStep(R.string.howto_5_step2, HelpMockupId.Setup),
            HowToStep(R.string.howto_5_step3, null),
            HowToStep(R.string.howto_5_step4, HelpMockupId.TimerMenu),
            HowToStep(R.string.howto_5_step5, HelpMockupId.Two),
            HowToStep(R.string.howto_5_step6, null),
        ),
        tip = R.string.howto_5_tip,
    ),

    /** Topic 6 (SCR-35 row 6). */
    JournalAndTaste(
        title = R.string.howto_6_title,
        line = R.string.howto_6_line,
        intro = R.string.howto_6_intro,
        steps = listOf(
            HowToStep(R.string.howto_6_step1, HelpMockupId.Saved),
            HowToStep(R.string.howto_6_step2, null),
            HowToStep(R.string.howto_6_step3, HelpMockupId.Session),
            HowToStep(R.string.howto_6_step4, null),
            HowToStep(R.string.howto_6_step5, null),
            HowToStep(R.string.howto_6_step6, null),
        ),
        tip = null,
    ),

    /** Topic 7 (SCR-35 row 7). */
    DataAndBackup(
        title = R.string.howto_7_title,
        line = R.string.howto_7_line,
        intro = R.string.howto_7_intro,
        steps = listOf(
            HowToStep(R.string.howto_7_step1, HelpMockupId.SettingsData),
            HowToStep(R.string.howto_7_step2, HelpMockupId.Data),
            HowToStep(R.string.howto_7_step3, null),
            HowToStep(R.string.howto_7_step4, null),
            HowToStep(R.string.howto_7_step5, null),
            HowToStep(R.string.howto_7_step6, null),
        ),
        tip = R.string.howto_7_tip,
    ),

    /** Topic 8 (SCR-35 row 8). */
    ReliableAlarm(
        title = R.string.howto_8_title,
        line = R.string.howto_8_line,
        intro = R.string.howto_8_intro,
        steps = listOf(
            HowToStep(R.string.howto_8_step1, HelpMockupId.Reliability),
            HowToStep(R.string.howto_8_step2, null),
            HowToStep(R.string.howto_8_step3, null),
            HowToStep(R.string.howto_8_step4, HelpMockupId.Banner),
            HowToStep(R.string.howto_8_step5, null),
        ),
        tip = R.string.howto_8_tip,
    ),
}

/** Test tags of the Anleitungen (SCR-35, SCR-36). */
object HowToTags {
    const val LIST = "HowToList"
    const val TOPIC = "HowToTopic"

    fun row(topic: HowToTopic) = "HowToRow_${topic.name}"
}
