package com.umamit.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every simple component in light („Washi") and dark („Steeping") at font scale
 * 1.0 and 2.0 (M1-04, 03 §6). Goldens: `src/test/screenshots/`, verified by
 * `verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ComponentScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}, fontScale={1}")
        fun parameters(): List<Array<Any>> = listOf(false, true).flatMap { dark ->
            listOf(1f, 2f).map { scale -> arrayOf<Any>(dark, scale) }
        }

        private const val TAG = "component"
    }

    @get:Rule
    val compose = createComposeRule()

    private val variant get() = "${if (dark) "dark" else "light"}_font${fontScale.toString().replace('.', '_')}"

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            UmamiTheme(darkTheme = dark, reduceMotion = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(
                        modifier = Modifier
                            .testTag(TAG)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = UmamiSpacing.Medium),
                    ) {
                        content()
                    }
                }
            }
        }
        compose.onNodeWithTag(TAG)
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    @Test
    fun pillButton() = capture("PillButton") {
        Column(
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            PillButton(text = "Pause", onClick = {}, icon = painterResource(UmamiIcons.Pause))
            PillButton(text = "Abgegossen", onClick = {})
            PillButton(text = "Später", onClick = {}, enabled = false)
        }
    }

    @Test
    fun sectionHeader() = capture("SectionHeader") {
        Column {
            SectionHeader(title = "Zuletzt gebrüht")
            SectionHeader(title = "Favoriten", onShowAll = {})
            SectionHeader(title = "Große Blätter für Grüße", onShowAll = {})
        }
    }

    @Test
    fun emptyState() = capture("EmptyState") {
        EmptyState(
            title = "Deine erste Tasse wartet.",
            body = "Brühe einen Tee, dann erscheint er hier im Journal.",
            actionLabel = "Aufguss starten",
            onAction = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Test
    fun primaryActionCard() = capture("PrimaryActionCard") {
        Column(
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            PrimaryActionCard(title = "Aufguss starten", onClick = {})
            PrimaryActionCard(title = "Schnelltimer", subtitle = "Ohne Tee, nur die Zeit", onClick = {})
        }
    }

    @Test
    fun kettleHint() = capture("KettleHint") {
        Column(
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
        ) {
            KettleHint(temperatureCelsius = 60, detail = "Aufguss 2 · 0:20")
            KettleHint(temperatureCelsius = 100)
        }
    }

    @Test
    fun statTriplet() = capture("StatTriplet") {
        Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
            StatTriplet(temperatureCelsius = 65, steepSeconds = 60, infusions = 3)
            StatTriplet(temperatureCelsius = null, steepSeconds = 14_400, infusions = null)
        }
    }

    @Test
    fun teaListItem() = capture("TeaListItem") {
        val types = UmamiTheme.extendedColors.teaTypes
        Column {
            TeaListItem(
                title = "Bio Sencha Hisui",
                meta = "8. Juni · 3 Min · 80 °C",
                rating = 4.5f,
                placeholderColor = types.sencha,
                onClick = {},
            )
            TeaListItem(
                title = "Gyokuro Asahi Hōjicha Kōridashi mit überlangem Namen",
                meta = "Heute · 1 Aufguss",
                placeholderColor = types.hojicha,
                onClick = {},
            )
            TeaListItem(
                title = "Genmaicha",
                meta = "Noch nie gebrüht",
                rating = 4f,
                placeholderColor = types.genmaicha,
                showDivider = false,
            )
        }
    }

    /** Umlauts, ß and ẞ in serif titles and sans overlines (03 §3.2). */
    @Test
    fun typeScale() = capture("TypeScale") {
        val type = MaterialTheme.typography
        Column {
            Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
                Text("Grüße aus Shizuoka", style = type.headlineMedium)
                Text("Bio Sencha Hisui · Größe Ö Ä Ü ẞ", style = type.titleLarge)
                Text("Hōjicha Kōridashi 0123456789", style = type.titleMedium)
                Text("Zieht 1:30 bei 70\u00A0°C. Süß und frisch.", style = type.bodyLarge)
            }
            SectionHeader(title = "Zuletzt gebrüht")
            SectionHeader(title = "Große Blätter")
        }
    }

    @Test
    fun reliabilityBanner() = capture("ReliabilityBanner") {
        Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin)) {
            ReliabilityBanner(
                message = "Exakte Wecker sind aus. Der Timer könnte sich verspäten.",
                onFix = {},
            )
        }
    }
}
