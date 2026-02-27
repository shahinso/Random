package com.facephotosender.data

import androidx.room.*

// ─── Room entities ────────────────────────────────────────────────────────────

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,            // WhatsApp phone number (E.164 format e.g. +14155552671)
    val faceEmbedding: ByteArray? = null,  // serialised float array
    val referenceImageUri: String? = null  // URI of the reference selfie
) {
    override fun equals(other: Any?) = other is Contact && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity(tableName = "matched_photos")
data class MatchedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val photoUri: String,
    val confidence: Float,
    val sentAt: Long? = null
)

// ─── DAO ─────────────────────────────────────────────────────────────────────

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAll(): List<Contact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?
}

@Dao
interface MatchedPhotoDao {
    @Query("SELECT * FROM matched_photos WHERE contactId = :contactId AND sentAt IS NULL")
    suspend fun getPendingForContact(contactId: Long): List<MatchedPhoto>

    @Query("SELECT * FROM matched_photos WHERE contactId = :contactId ORDER BY confidence DESC")
    suspend fun getAllForContact(contactId: Long): List<MatchedPhoto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: MatchedPhoto): Long

    @Query("UPDATE matched_photos SET sentAt = :timestamp WHERE id = :id")
    suspend fun markSent(id: Long, timestamp: Long)

    @Query("DELETE FROM matched_photos WHERE contactId = :contactId")
    suspend fun deleteForContact(contactId: Long)
}

// ─── Database ────────────────────────────────────────────────────────────────

@Database(entities = [Contact::class, MatchedPhoto::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun matchedPhotoDao(): MatchedPhotoDao
}
