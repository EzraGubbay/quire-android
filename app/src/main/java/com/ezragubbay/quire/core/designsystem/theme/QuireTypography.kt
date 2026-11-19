package com.ezragubbay.quire.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ezragubbay.quire.R

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily =
    FontFamily(
        listOf(
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
        ).map { weight ->
            Font(
                resId = resId,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

/** Display face (Fraunces, SIL OFL 1.1): headings and large numerals. */
val QuireDisplayFont: FontFamily = variableFamily(R.font.fraunces)

/** Reading face (Literata, SIL OFL 1.1): body text and long-form content. */
val QuireReadingFont: FontFamily = variableFamily(R.font.literata)

/** UI face for titles and labels: the platform sans, for crisp small controls. */
val QuireUiFont: FontFamily = FontFamily.Default

/** Display/headline in [QuireDisplayFont], body in [QuireReadingFont], titles/labels in [QuireUiFont]. */
val QuireTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = QuireDisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = QuireDisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = QuireDisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = QuireDisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = QuireDisplayFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = QuireDisplayFont,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = QuireUiFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = QuireUiFont,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = QuireUiFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = QuireReadingFont,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.2.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = QuireReadingFont,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.2.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = QuireReadingFont,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.3.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = QuireUiFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = QuireUiFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = QuireUiFont,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
