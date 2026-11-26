package com.ezragubbay.quire.di

import android.content.Context
import com.ezragubbay.quire.core.data.course.CourseRepository
import com.ezragubbay.quire.core.database.UploadDao
import com.ezragubbay.quire.core.network.UploadApi
import com.ezragubbay.quire.core.upload.ContentResolverUriSource
import com.ezragubbay.quire.core.upload.UploadDeps
import com.ezragubbay.quire.core.upload.UploadPaths
import com.ezragubbay.quire.core.upload.UploadQueue
import com.ezragubbay.quire.core.upload.UploadRepository
import com.ezragubbay.quire.core.upload.UploadScheduler
import com.ezragubbay.quire.core.upload.UploadStager
import com.ezragubbay.quire.core.upload.WorkManagerUploadScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UploadModule {
    @Provides
    @Singleton
    fun uploadPaths(
        @ApplicationContext context: Context,
    ) = UploadPaths(context.filesDir)

    @Provides
    @Singleton
    fun uploadScheduler(
        @ApplicationContext context: Context,
    ): UploadScheduler = WorkManagerUploadScheduler(context)

    @Provides
    @Singleton
    fun uploadDeps(
        dao: UploadDao,
        api: UploadApi,
        paths: UploadPaths,
        courses: CourseRepository,
    ) = UploadDeps(dao, api, paths, refresh = { slug -> courses.refreshCourse(slug) })

    @Provides
    @Singleton
    fun uploadQueue(
        @ApplicationContext context: Context,
        dao: UploadDao,
        scheduler: UploadScheduler,
        paths: UploadPaths,
    ): UploadQueue =
        UploadRepository(dao, scheduler, paths, UploadStager(ContentResolverUriSource(context.contentResolver), paths))
}
