package com.ezragubbay.quire.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light / dark / RTL (Hebrew locale) / large-font variants; apply to every component preview. */
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "rtl", showBackground = true, locale = "he")
@Preview(name = "large-font", showBackground = true, fontScale = 1.3f)
annotation class QuirePreviews
