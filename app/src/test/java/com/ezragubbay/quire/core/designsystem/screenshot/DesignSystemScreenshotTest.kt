package com.ezragubbay.quire.core.designsystem.screenshot

import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * Renders every design-system `@Preview` (the component kit via `QuirePreviews` and the palette/type specimens)
 * and compares it with the golden in `app/src/test/screenshots/`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DesignSystemScreenshotTest(
    private val preview: ComposablePreview<AndroidPreviewInfo>,
) {
    @Test
    fun matchesGolden() {
        val id = AndroidPreviewScreenshotIdBuilder(preview).ignoreClassName().build()
        preview.captureRoboImage(
            filePath = "src/test/screenshots/$id.png",
            roborazziOptions = tolerantOptions(),
        )
    }

    private companion object {
        /** Small tolerance so goldens recorded on macOS pass on Linux CI (font anti-aliasing). */
        // Built lazily: RoborazziOptions needs the Robolectric sandbox, which does not exist while parameters load.
        fun tolerantOptions() =
            RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

        @JvmStatic
        @Parameters(name = "{0}")
        fun previews(): List<ComposablePreview<AndroidPreviewInfo>> =
            AndroidComposablePreviewScanner()
                .scanPackageTrees("com.ezragubbay.quire.core.designsystem")
                .includePrivatePreviews()
                .getPreviews()
    }
}
