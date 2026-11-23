package com.ezragubbay.quire.push

import android.content.Context
import com.ezragubbay.quire.R

/** [PushTexts] from string resources (localised, RTL-safe through the platform). */
class ResourcePushTexts(
    private val context: Context,
) : PushTexts {
    private fun s(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    override val appName get() = s(R.string.app_name)

    override fun fileTypeLabel(type: String): String =
        s(
            when (type) {
                "lecture" -> R.string.file_type_lecture
                "recitation" -> R.string.file_type_recitation
                "homework" -> R.string.file_type_homework
                "exam" -> R.string.file_type_exam
                "book" -> R.string.file_type_book
                "paper" -> R.string.file_type_paper
                else -> R.string.file_type_other
            },
        )

    override fun newFileTyped(
        typeLabel: String,
        title: String,
    ) = s(R.string.push_new_file_typed, typeLabel, title)

    override fun newFileIn(courseName: String?) = s(R.string.push_new_file_in, courseName ?: s(R.string.push_a_course))

    override fun newFiles(count: Int): String =
        context.resources.getQuantityString(R.plurals.push_new_files, count, count)

    override fun homeworkDueTitle(
        number: String,
        due: String,
    ) = s(R.string.push_homework_due_title, number, due)

    override fun dueToday(time: String) = s(R.string.push_due_today, time)

    override fun dueTomorrow(time: String) = s(R.string.push_due_tomorrow, time)

    override fun dueOn(
        date: String,
        time: String,
    ) = s(R.string.push_due_on, date, time)

    override val homeworkFallbackBody get() = s(R.string.push_homework_fallback_body)
    override val noteReadyTitle get() = s(R.string.push_note_ready_title)

    override fun noteReadyFallback(courseName: String?) =
        s(R.string.push_note_ready_fallback, courseName ?: s(R.string.push_a_course))

    override val noteFailedTitle get() = s(R.string.push_note_failed_title)
    override val noteFailedBody get() = s(R.string.push_note_failed_body)
    override val testTitle get() = s(R.string.push_test_title)
    override val testBody get() = s(R.string.push_test_body)
}
