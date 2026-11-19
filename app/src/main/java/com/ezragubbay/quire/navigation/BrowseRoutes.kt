package com.ezragubbay.quire.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.serialization.Serializable

/** The course screen for [slug]; pushed over the shell from the Courses list. */
@Serializable
data class CourseRoute(
    val slug: String,
)

/** Full-screen search; pushed over the shell from the Courses top bar. */
@Serializable
data object SearchRoute

/** Placeholder destination, replaced by the real course screen (QMOB-E4-S3). */
@Composable
internal fun CoursePlaceholder(slug: String) {
    Box(Modifier.fillMaxSize().testTag("screen-course"), contentAlignment = Alignment.Center) { Text(slug) }
}

/** Placeholder destination, replaced by the real search screen (QMOB-E4-S5). */
@Composable
internal fun SearchPlaceholder() {
    Box(Modifier.fillMaxSize().testTag("screen-search"), contentAlignment = Alignment.Center) { Text("Search") }
}
