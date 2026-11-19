package com.ezragubbay.quire.core.data.course

import com.ezragubbay.quire.core.network.ApiResult
import com.ezragubbay.quire.core.network.BrowseApi
import com.ezragubbay.quire.core.network.CourseCountsDto
import com.ezragubbay.quire.core.network.CourseDetailDto
import com.ezragubbay.quire.core.network.CourseFileDto
import com.ezragubbay.quire.core.network.CourseSessionDto
import com.ezragubbay.quire.core.network.CourseSummaryDto
import com.ezragubbay.quire.core.network.NoteDto
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

fun summaryDto(
    slug: String,
    name: String = slug,
    semester: String? = "Winter 2026",
    fileCount: Int = 1,
) = CourseSummaryDto(slug, "id-$slug", name, "0001", semester, "lecture", fileCount)

fun detailDto(
    s: CourseSummaryDto,
    counts: CourseCountsDto,
) = CourseDetailDto(s.slug, s.id, s.name, s.code, s.semester, s.type, s.fileCount, counts)

fun fileDto(
    id: String,
    title: String = id,
    type: String = "lecture",
) = CourseFileDto(id, title, type, null, null, 3, 100L, true, false, false, "2026-11-02T09:30:00Z")

fun sessionDto(number: Int) =
    CourseSessionDto("lecture", number, "Lecture $number", "2026-11-0$number", listOf("f$number"), null)

fun noteDto(id: String) = NoteDto(id, "slug-$id", "Note $id", null, "2026-11-02T12:00:00Z")

val networkDown = ApiResult.Failure.Network(IOException("no signal"))

class FakeBrowseApi : BrowseApi {
    var courses: ApiResult<List<CourseSummaryDto>> = ApiResult.Success(emptyList())
    var detail: (String) -> ApiResult<CourseDetailDto> = { slug ->
        ApiResult.Success(detailDto(summaryDto(slug), CourseCountsDto(mapOf("lecture" to 1), emptyMap(), 0)))
    }
    var sessions: ApiResult<List<CourseSessionDto>> = ApiResult.Success(emptyList())
    var files: ApiResult<List<CourseFileDto>> = ApiResult.Success(emptyList())
    var notes: ApiResult<List<NoteDto>> = ApiResult.Success(emptyList())

    /** When set, [courses] suspends until completed (simulates a slow network). */
    var gate: CompletableDeferred<Unit>? = null
    var courseCalls = 0

    override suspend fun courses(): ApiResult<List<CourseSummaryDto>> {
        courseCalls++
        gate?.await()
        return courses
    }

    override suspend fun course(slug: String) = detail(slug)

    override suspend fun sessions(slug: String) = sessions

    override suspend fun files(slug: String) = files

    override suspend fun notes(slug: String) = notes
}
