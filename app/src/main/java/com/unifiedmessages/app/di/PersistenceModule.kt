package com.unifiedmessages.app.di

import android.content.Context
import androidx.room.Room
import com.unifiedmessages.app.data.local.MIGRATION_1_2
import com.unifiedmessages.app.data.local.MIGRATION_2_3
import com.unifiedmessages.app.data.local.MIGRATION_3_4
import com.unifiedmessages.app.data.local.MIGRATION_4_5
import com.unifiedmessages.app.data.local.MIGRATION_5_6
import com.unifiedmessages.app.data.local.MIGRATION_6_7
import com.unifiedmessages.app.data.local.MIGRATION_7_8
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UnifiedMessagesDatabase =
        Room.databaseBuilder(context, UnifiedMessagesDatabase::class.java, "unified_messages.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
}
