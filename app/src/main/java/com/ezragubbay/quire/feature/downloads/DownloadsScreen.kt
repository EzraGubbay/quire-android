package com.ezragubbay.quire.feature.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Placeholder Downloads destination root. Real content lands in a later story. */
@Composable
fun DownloadsScreen() {
    Box(modifier = Modifier.fillMaxSize().testTag("screen-downloads"), contentAlignment = Alignment.Center) {
        Text(text = "Downloads")
    }
}
