package com.facephotosender

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.facephotosender.data.*
import com.facephotosender.face.FaceRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val progress: Int, val total: Int) : ScanState()
    data class Done(val results: Map<Contact, List<MatchedPhoto>>) : ScanState()
    data class Error(val message: String) : ScanState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DatabaseProvider.get(application)
    private val photoRepo = PhotoRepository(application)
    private val faceEngine = FaceRecognitionEngine(application)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    init { loadContacts() }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _contacts.value = db.contactDao().getAll()
        }
    }

    fun saveContact(contact: Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            db.contactDao().insert(contact)
            loadContacts()
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            db.contactDao().delete(contact)
            db.matchedPhotoDao().deleteForContact(contact.id)
            loadContacts()
        }
    }

    /**
     * Scans the last 50 gallery photos, detects faces, and matches each face
     * against every registered contact.
     */
    fun scanPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val contacts = db.contactDao().getAll()
            if (contacts.isEmpty()) {
                _scanState.value = ScanState.Error("No contacts registered. Add a contact first.")
                return@launch
            }

            // Pre-load contact embeddings
            val contactEmbeddings = contacts.mapNotNull { contact ->
                contact.faceEmbedding?.let { bytes ->
                    contact to faceEngine.bytesToEmbedding(bytes)
                }
            }

            if (contactEmbeddings.isEmpty()) {
                _scanState.value = ScanState.Error("No face data for contacts. Enrol a face for each contact.")
                return@launch
            }

            val photos = photoRepo.getRecentPhotos()
            val resultMap = mutableMapOf<Contact, MutableList<MatchedPhoto>>()
            contacts.forEach { resultMap[it] = mutableListOf() }

            photos.forEachIndexed { index, photo ->
                _scanState.value = ScanState.Scanning(index + 1, photos.size)
                try {
                    val faces = faceEngine.detectFacesFromUri(photo.uri)
                    faces.forEach { faceResult ->
                        contactEmbeddings.forEach { (contact, refEmb) ->
                            val sim = faceEngine.similarity(faceResult.embedding, refEmb)
                            if (sim >= FaceRecognitionEngine.SIMILARITY_THRESHOLD) {
                                val matched = MatchedPhoto(
                                    contactId  = contact.id,
                                    photoUri   = photo.uri.toString(),
                                    confidence = sim
                                )
                                val id = db.matchedPhotoDao().insert(matched)
                                resultMap[contact]?.add(matched.copy(id = id))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip individual photo errors silently
                }
            }

            _scanState.value = ScanState.Done(resultMap.filter { it.value.isNotEmpty() })
        }
    }

    fun markPhotoSent(photoId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.matchedPhotoDao().markSent(photoId, System.currentTimeMillis())
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceEngine.close()
    }
}
