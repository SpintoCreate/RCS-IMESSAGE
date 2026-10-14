package com.unifiedmessages.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactsDao {
    @Query("SELECT * FROM cached_contacts ORDER BY displayName COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query(
        """SELECT * FROM cached_contacts
           WHERE :query = '' OR displayName LIKE '%' || :query || '%' COLLATE NOCASE
              OR (:digits != '' AND numberDigits LIKE '%' || :digits || '%')
           ORDER BY displayName COLLATE NOCASE, id""",
    )
    fun observeSearch(query: String, digits: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM cached_contacts WHERE normalizedNumber = :number ORDER BY displayName COLLATE NOCASE, id LIMIT 1")
    suspend fun findByNumber(number: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>)

    @Query("DELETE FROM cached_contacts")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(contacts: List<ContactEntity>) {
        deleteAll()
        insertAll(contacts)
    }
}
