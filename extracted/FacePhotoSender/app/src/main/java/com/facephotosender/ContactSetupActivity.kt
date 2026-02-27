package com.facephotosender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.facephotosender.data.Contact
import com.facephotosender.data.DatabaseProvider
import com.facephotosender.databinding.ActivityContactSetupBinding
import com.facephotosender.face.FaceRecognitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactSetupActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CONTACT_ID = "contact_id"
        fun start(context: Context, contactId: Long) {
            context.startActivity(
                Intent(context, ContactSetupActivity::class.java)
                    .putExtra(EXTRA_CONTACT_ID, contactId)
            )
        }
    }

    private lateinit var binding: ActivityContactSetupBinding
    private val db by lazy { DatabaseProvider.get(this) }
    private val faceEngine by lazy { FaceRecognitionEngine(this) }

    private var existingContact: Contact? = null
    private var selectedImageUri: Uri? = null
    private var faceEmbedding: FloatArray? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        selectedImageUri = uri
        Glide.with(this).load(uri).circleCrop().into(binding.ivFacePreview)
        processReferenceImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Add / Edit Contact"

        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
        if (contactId != -1L) loadExistingContact(contactId)

        binding.btnPickFacePhoto.setOnClickListener {
            imagePicker.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            saveContact()
        }
    }

    private fun loadExistingContact(id: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val contact = db.contactDao().getById(id) ?: return@launch
            existingContact = contact
            withContext(Dispatchers.Main) {
                binding.etName.setText(contact.name)
                binding.etPhone.setText(contact.phone)
                contact.referenceImageUri?.let { uriStr ->
                    Glide.with(this@ContactSetupActivity)
                        .load(Uri.parse(uriStr))
                        .circleCrop()
                        .into(binding.ivFacePreview)
                }
                if (contact.faceEmbedding != null) {
                    binding.tvFaceStatus.text = "✅ Face enrolled"
                    faceEmbedding = faceEngine.bytesToEmbedding(contact.faceEmbedding)
                }
            }
        }
    }

    private fun processReferenceImage(uri: Uri) {
        binding.tvFaceStatus.text = "Detecting face…"
        binding.btnSave.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val faces = faceEngine.detectFacesFromUri(uri)
            withContext(Dispatchers.Main) {
                when {
                    faces.isEmpty() -> {
                        binding.tvFaceStatus.text = "❌ No face detected. Try a clearer selfie."
                        faceEmbedding = null
                        binding.btnSave.isEnabled = true
                    }
                    faces.size > 1 -> {
                        binding.tvFaceStatus.text =
                            "⚠️ Multiple faces detected (${faces.size}). Using the first face found."
                        faceEmbedding = faces.first().embedding
                        binding.tvFaceStatus.text += "\n✅ Face enrolled"
                        binding.btnSave.isEnabled = true
                    }
                    else -> {
                        faceEmbedding = faces.first().embedding
                        binding.tvFaceStatus.text = "✅ Face enrolled successfully"
                        binding.btnSave.isEnabled = true
                    }
                }
            }
        }
    }

    private fun saveContact() {
        val name  = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        if (name.isBlank()) {
            binding.etName.error = "Name is required"
            return
        }
        if (phone.isBlank()) {
            binding.etPhone.error = "Phone number is required (e.g. +14155552671)"
            return
        }

        val embBytes = faceEmbedding?.let { faceEngine.embeddingToBytes(it) }
            ?: existingContact?.faceEmbedding

        val contact = (existingContact ?: Contact(name = "", phone = "")).copy(
            name              = name,
            phone             = phone,
            faceEmbedding     = embBytes,
            referenceImageUri = selectedImageUri?.toString() ?: existingContact?.referenceImageUri
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.contactDao().insert(contact)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ContactSetupActivity, "Contact saved!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceEngine.close()
    }
}
