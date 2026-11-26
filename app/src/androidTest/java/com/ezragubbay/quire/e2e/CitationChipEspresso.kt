package com.ezragubbay.quire.e2e

import android.text.Spanned
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import com.ezragubbay.quire.feature.chat.conversation.CitationChipSpan
import org.hamcrest.Description
import org.hamcrest.Matcher

/**
 * Inline citation chips are spans inside the answer's TextView, not Compose nodes, so the journey finds and taps them
 * through Espresso: [withCitationChip] matches the TextView holding the span with this test tag, and
 * [tapCitationChip] performs a real touch at the span's on-screen position (exercising the link movement method).
 */
fun withCitationChip(tag: String): Matcher<View> =
    object : BoundedMatcher<View, TextView>(TextView::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("TextView with citation chip $tag")
        }

        override fun matchesSafely(view: TextView): Boolean = chipSpan(view, tag) != null
    }

fun tapCitationChip(tag: String): ViewAction =
    object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "tap citation chip $tag"

        override fun perform(
            uiController: UiController,
            view: View,
        ) {
            GeneralClickAction(
                Tap.SINGLE,
                CoordinatesProvider { v -> chipCenter(v as TextView, tag) },
                Press.FINGER,
                InputDevice.SOURCE_UNKNOWN,
                MotionEvent.BUTTON_PRIMARY,
            ).perform(uiController, view)
        }
    }

private fun chipSpan(
    view: TextView,
    tag: String,
): CitationChipSpan? {
    val text = view.text as? Spanned ?: return null
    return text.getSpans(0, text.length, CitationChipSpan::class.java).firstOrNull { it.testTag == tag }
}

private fun chipCenter(
    view: TextView,
    tag: String,
): FloatArray {
    val span = checkNotNull(chipSpan(view, tag)) { "no chip $tag" }
    val text = view.text as Spanned
    val start = text.getSpanStart(span)
    val end = text.getSpanEnd(span)
    val layout = checkNotNull(view.layout) { "TextView not laid out" }
    val line = layout.getLineForOffset(start)
    val x = (layout.getPrimaryHorizontal(start) + layout.getPrimaryHorizontal(end)) / 2f
    val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    return floatArrayOf(
        loc[0] + view.totalPaddingLeft + x - view.scrollX,
        loc[1] + view.totalPaddingTop + y - view.scrollY,
    )
}
