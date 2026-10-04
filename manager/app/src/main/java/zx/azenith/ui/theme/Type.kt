/*
 * Copyright (C) 2026-2027 KowX
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zx.azenith.ui.theme


import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import zx.azenith.R


/** Latin UI font. Google Sans Flex, SIL OFL 1.1 (assets/licenses). */
val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_medium, FontWeight.Medium),
    Font(R.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_flex_bold, FontWeight.Bold),
)

/** Arabic UI font. Noto Kufi Arabic, SIL OFL 1.1 (assets/licenses). It also carries Latin glyphs. */
val NotoKufiArabic = FontFamily(
    Font(R.font.noto_kufi_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_kufi_arabic_medium, FontWeight.Medium),
    Font(R.font.noto_kufi_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.noto_kufi_arabic_bold, FontWeight.Bold),
)

/** Brand wordmark and big numbers always use Google Sans Flex, whatever the locale. */
val BrandFontFamily = GoogleSansFlex

private val arabicScriptLanguages = setOf("ar", "fa", "ur", "ps", "ckb", "sd", "ug")

private fun style(family: FontFamily, weight: FontWeight, size: Float, line: Float, tracking: Float = 0f) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

/**
 * Compact Material 3 type scale. Sizes sit a step below the M3 defaults so
 * buttons and cards stay small on phones; Arabic gets a little more line
 * height because Kufi glyphs are taller.
 */
fun nextCoreTypography(family: FontFamily, arabic: Boolean): Typography {
    val l = if (arabic) 1.12f else 1f
    return Typography(
        displayLarge = style(family, FontWeight.Normal, 52f, 60f * l, -0.25f),
        displayMedium = style(family, FontWeight.SemiBold, 40f, 48f * l),
        displaySmall = style(family, FontWeight.Normal, 32f, 40f * l),
        headlineLarge = style(family, FontWeight.SemiBold, 28f, 36f * l),
        headlineMedium = style(family, FontWeight.SemiBold, 24f, 32f * l),
        headlineSmall = style(family, FontWeight.SemiBold, 21f, 28f * l),
        titleLarge = style(family, FontWeight.Bold, 19f, 26f * l),
        titleMedium = style(family, FontWeight.SemiBold, 15f, 22f * l, 0.1f),
        titleSmall = style(family, FontWeight.Medium, 13.5f, 20f * l, 0.1f),
        bodyLarge = style(family, FontWeight.Normal, 15f, 22f * l, 0.3f),
        bodyMedium = style(family, FontWeight.Normal, 13.5f, 20f * l, 0.2f),
        bodySmall = style(family, FontWeight.Normal, 12f, 17f * l, 0.3f),
        labelLarge = style(family, FontWeight.Medium, 13.5f, 20f * l, 0.1f),
        labelMedium = style(family, FontWeight.Medium, 12f, 16f * l, 0.4f),
        labelSmall = style(family, FontWeight.Medium, 11f, 15f * l, 0.4f),
    )
}

/** Picks Noto Kufi Arabic for Arabic-script locales and Google Sans Flex otherwise. */
@Composable
fun rememberNextCoreTypography(): Typography {
    val locales = LocalConfiguration.current.locales
    val lang = if (locales.isEmpty) "" else locales[0].language
    val arabic = lang in arabicScriptLanguages
    return remember(arabic) {
        nextCoreTypography(if (arabic) NotoKufiArabic else GoogleSansFlex, arabic)
    }
}

val Typography = nextCoreTypography(GoogleSansFlex, arabic = false)
