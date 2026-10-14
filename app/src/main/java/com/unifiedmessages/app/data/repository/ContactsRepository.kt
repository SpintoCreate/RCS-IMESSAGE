package com.unifiedmessages.app.data.repository

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.unifiedmessages.app.data.local.ContactEntity
import com.unifiedmessages.app.data.local.UnifiedMessagesDatabase
import com.unifiedmessages.app.sms.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CachedContact(
    val id: Long,
    val displayName: String,
    val normalizedNumber: String,
    val photoUri: String?,
)

/** Mirrors the user's readable phone contacts into Room while READ_CONTACTS is granted. */
@Singleton
class ContactsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: UnifiedMessagesDatabase,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val dao = database.contactsDao()
    private val refreshMutex = Mutex()
    private var observerScope: CoroutineScope? = null
    private var refreshJob: Job? = null
    private var observing = false

    val contacts: Flow<List<CachedContact>> = dao.observeAll().map { rows ->
        if (hasPermission()) rows.map { it.toCachedContact() } else emptyList()
    }

    fun search(query: String): Flow<List<CachedContact>> {
        val trimmed = query.trim()
        val digits = trimmed.filter(Char::isDigit)
        return dao.observeSearch(trimmed, digits).map { rows ->
            if (hasPermission()) rows.map { it.toCachedContact() } else emptyList()
        }
    }

    suspend fun findByNumber(raw: String): CachedContact? {
        if (!hasPermission()) return null
        val number = PhoneNumbers.normalizeToE164(raw, PhoneNumbers.deviceRegion(context)) ?: return null
        return dao.findByNumber(number)?.toCachedContact()
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    /** A denied/revoked permission removes cached personal details as well as hiding them. */
    suspend fun refreshIfPermitted(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (!hasPermission()) {
                dao.deleteAll()
                return@withLock false
            }
            val rows = try {
                readPhoneRows()
            } catch (_: SecurityException) {
                dao.deleteAll()
                return@withLock false
            } catch (_: RuntimeException) {
                // Keep the last complete cache if the contacts provider is temporarily unavailable.
                return@withLock false
            }
            if (!hasPermission()) {
                dao.deleteAll()
                return@withLock false
            }
            dao.replaceAll(rows)
            true
        }
    }

    /** The caller owns [scope] and calls [stopObserving] when leaving the contacts flow. */
    fun startObserving(scope: CoroutineScope) {
        if (observing) return
        if (!hasPermission()) {
            scope.launch { refreshIfPermitted() }
            return
        }
        observerScope = scope
        try {
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            resolver.registerContentObserver(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, true, observer)
            observing = true
        } catch (_: SecurityException) {
            resolver.unregisterContentObserver(observer)
            observerScope = null
            scope.launch { refreshIfPermitted() }
        }
    }

    fun stopObserving() {
        refreshJob?.cancel()
        refreshJob = null
        observerScope = null
        if (observing) {
            resolver.unregisterContentObserver(observer)
            observing = false
        }
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshJob?.cancel()
            refreshJob = observerScope?.launch {
                delay(200)
                refreshIfPermitted()
            }
        }
    }

    private fun readPhoneRows(): List<ContactEntity> {
        val result = ArrayList<ContactEntity>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
        )
        val region = PhoneNumbers.deviceRegion(context)
        val cursor = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)
            ?: error("The contacts provider is unavailable.")
        cursor.use {
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            while (cursor.moveToNext()) {
                val raw = cursor.getString(numberIndex) ?: continue
                val normalized = PhoneNumbers.normalizeToE164(raw, region) ?: continue
                result += ContactEntity(
                    id = cursor.getLong(idIndex),
                    displayName = cursor.getString(nameIndex)?.takeIf(String::isNotBlank) ?: normalized,
                    normalizedNumber = normalized,
                    numberDigits = normalized.filter(Char::isDigit),
                    photoUri = cursor.getString(photoIndex),
                )
            }
        }
        return result
    }

    private fun ContactEntity.toCachedContact(): CachedContact = CachedContact(
        id = id,
        displayName = displayName,
        normalizedNumber = normalizedNumber,
        photoUri = photoUri,
    )
}
