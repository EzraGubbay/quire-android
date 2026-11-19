package com.ezragubbay.quire.feature.viewer.pdf

import com.ezragubbay.quire.core.database.ReadingPositionDao
import com.ezragubbay.quire.core.database.ReadingPositionEntity
import javax.inject.Inject

/** Last-read page per file (0-based), persisted in Room so reopening resumes where the reader left off. */
class ReadingPositionStore(
    private val dao: ReadingPositionDao,
    private val clock: () -> Long,
) {
    @Inject
    constructor(dao: ReadingPositionDao) : this(dao, System::currentTimeMillis)

    suspend fun lastPage(fileId: String): Int? = dao.get(fileId)?.page

    suspend fun save(
        fileId: String,
        page: Int,
    ) = dao.upsert(ReadingPositionEntity(fileId, page, clock()))
}
