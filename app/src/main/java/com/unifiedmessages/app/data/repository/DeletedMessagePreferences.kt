package com.unifiedmessages.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.messageDisplayDataStore by preferencesDataStore(name = "message_display")

/** Controls presentation only. A provider's deletion marker and original text stay in Room. */
@Singleton
class DeletedMessagePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val showSenderDeletedMessages: Flow<Boolean> = context.messageDisplayDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it[SHOW_SENDER_DELETED] ?: true }

    suspend fun setShowSenderDeletedMessages(show: Boolean) {
        context.messageDisplayDataStore.edit { it[SHOW_SENDER_DELETED] = show }
    }

    private companion object {
        val SHOW_SENDER_DELETED = booleanPreferencesKey("show_sender_deleted_messages")
    }
}
