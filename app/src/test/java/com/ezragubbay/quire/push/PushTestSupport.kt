package com.ezragubbay.quire.push

import com.ezragubbay.quire.core.network.fixture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.rules.ExternalResource
import java.util.Collections

/** Captures [PushLog] lines (JVM tests have no `android.util.Log`) and restores the real sink afterwards. */
class PushLogRule : ExternalResource() {
    val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var original: (Int, String) -> Unit

    override fun before() {
        original = PushLog.sink
        PushLog.sink = { _, message -> lines += message }
    }

    override fun after() {
        PushLog.sink = original
    }
}

/** A vendored `push.*.json` fixture as the FCM data map the app receives (every value a string). */
fun pushData(name: String): Map<String, String> =
    Json
        .parseToJsonElement(fixture(name))
        .jsonObject
        .mapValues { it.value.jsonPrimitive.content }

/** Plain English [PushTexts] that echo their arguments, so mapping tests read without resources. */
object EnglishTexts : PushTexts {
    override val appName = "Quire"

    override fun fileTypeLabel(type: String) = type.ifEmpty { "file" }

    override fun newFileTyped(
        typeLabel: String,
        title: String,
    ) = "New $typeLabel: $title"

    override fun newFileIn(courseName: String?) = "New file in ${courseName ?: "a course"}"

    override fun newFiles(count: Int) = "$count new files"

    override fun homeworkDueTitle(
        number: String,
        due: String,
    ) = "Homework $number due $due"

    override fun dueToday(time: String) = "today $time"

    override fun dueTomorrow(time: String) = "tomorrow $time"

    override fun dueOn(
        date: String,
        time: String,
    ) = "$date $time"

    override val homeworkFallbackBody = "Open Quire for the details."
    override val noteReadyTitle = "Study note ready"

    override fun noteReadyFallback(courseName: String?) = "A new study note is ready in ${courseName ?: "a course"}."

    override val noteFailedTitle = "Note synthesis failed"
    override val noteFailedBody = "Open Quire on the web to retry."
    override val testTitle = "Quire notifications work"
    override val testBody = "Test sent from your server."
}

/** Records token calls; [token] hands out [tokens] in order, advancing after each [delete]. */
class FakeTokenSource(
    override val available: Boolean = true,
    private val tokens: List<String> = listOf("fcm-token-1", "fcm-token-2", "fcm-token-3"),
) : FcmTokenSource {
    val calls = mutableListOf<String>()
    var autoInit: Boolean? = null
    var failToken: Boolean = false
    private var index = 0

    override suspend fun token(): String? {
        calls += "token"
        if (failToken) error("SERVICE_NOT_AVAILABLE")
        return tokens.getOrNull(index)
    }

    override suspend fun delete() {
        calls += "delete"
        index++
    }

    override fun setAutoInitEnabled(enabled: Boolean) {
        calls += "autoInit=$enabled"
        autoInit = enabled
    }
}

class FakeRegistrationScheduler : PushRegistrationScheduler {
    var enqueued = 0
    var cancelled = 0

    override fun enqueue() {
        enqueued++
    }

    override fun cancel() {
        cancelled++
    }
}
