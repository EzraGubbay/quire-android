package com.ezragubbay.quire.navigation

import kotlinx.serialization.Serializable

/** The course screen for [slug]; pushed over the shell from the Courses list. */
@Serializable
data class CourseRoute(
    val slug: String,
)

/** Full-screen search; pushed over the shell from the Courses top bar. */
@Serializable
data object SearchRoute
