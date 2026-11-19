package com.ezragubbay.quire.di

import android.content.Context
import androidx.room.Room
import com.ezragubbay.quire.core.data.course.CourseRepository
import com.ezragubbay.quire.core.data.course.SearchRepository
import com.ezragubbay.quire.core.database.ALL_MIGRATIONS
import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.network.BrowseApi
import com.ezragubbay.quire.core.network.QuireClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Offline cache graph: the Room database, its DAOs and the repositories on top of it. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Suppress("SpreadOperator") // one-time spread of the (small) migration list at startup
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): QuireDatabase =
        Room
            .databaseBuilder(context, QuireDatabase::class.java, QuireDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun courseDao(db: QuireDatabase) = db.courseDao()

    @Provides
    fun sessionDao(db: QuireDatabase) = db.sessionDao()

    @Provides
    fun fileDao(db: QuireDatabase) = db.fileDao()

    @Provides
    fun noteDao(db: QuireDatabase) = db.noteDao()

    @Provides
    fun downloadDao(db: QuireDatabase) = db.downloadDao()

    @Provides
    fun browseApi(client: QuireClient): BrowseApi = client

    @Provides
    @Singleton
    fun courseRepository(
        db: QuireDatabase,
        api: BrowseApi,
    ): CourseRepository = CourseRepository(db, api)

    @Provides
    @Singleton
    fun searchRepository(db: QuireDatabase): SearchRepository = SearchRepository(db)
}
