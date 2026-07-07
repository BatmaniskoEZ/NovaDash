package com.novadash.car

import com.novadash.data.MomentsRepository
import com.novadash.data.TagPresetsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the Car App classes (not Hilt-managed) reach the shared singleton repositories. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MomentsEntryPoint {
    fun momentsRepository(): MomentsRepository
    fun tagPresetsRepository(): TagPresetsRepository
}
