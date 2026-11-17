package com.umamit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.umamit.core.designsystem.theme.UmamiTheme

/**
 * The leaf placeholder's colour by the tea's *Sorte* [teaType] (03 §3.5), for a tea without a photo: the catalog's
 * and *Meine Tees*' rule (D-123). The Journal keys its colour by the style instead and keeps its own.
 */
@Composable
fun teaTypePlaceholderColor(teaType: String?): Color {
    val colors = UmamiTheme.extendedColors.teaTypes
    val type = teaType.orEmpty().lowercase()
    return when {
        "gyokuro" in type -> colors.gyokuro
        "kabuse" in type -> colors.kabusecha
        "shincha" in type -> colors.shincha
        "sencha" in type -> colors.sencha
        "matcha" in type -> colors.matcha
        "kukicha" in type || "karigane" in type -> colors.kukichaKarigane
        "hojicha" in type || "houjicha" in type -> colors.hojicha
        "genmaicha" in type -> colors.genmaicha
        else -> colors.other
    }
}
