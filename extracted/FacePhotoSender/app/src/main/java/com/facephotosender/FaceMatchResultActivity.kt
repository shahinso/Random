package com.facephotosender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.facephotosender.data.Contact
import com.facephotosender.data.DatabaseProvider
import com.facephotosender.data.MatchedPhoto
import com.facephotosender.databinding.ActivityFaceMatchResultBinding
import com.facephotosender.whatsapp.WhatsAppSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FaceMatchResultActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_CONTACT_ID = "contact_id"
        fun start(context: Context, contactId: Long) {
            context.startActivity(
                Intent(context, FaceMatchResultActivity::class.java)
                    .putExtra(EXTRA_CONTACT_ID, contactId)
            )
        }
    }

    private lateinit var binding: ActivityFaceMatchResultBinding
    private val db by lazy { DatabaseProvider.get(this) }
    private var contact: Contact? = null
    private var photos: List<MatchedPhoto> = emptyList()
    private val selected = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceMatchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
        loadData(contactId)

        binding.btnSendSelected.setOnClickListener { sendSelected() }
    }

    private fun loadData(contactId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val c = db.contactDao().getById(contactId) ?: return@launch
            val p = db.matchedPhotoDao().getPendingForContact(contactId)
            contact = c; photos = p
            withContext(Dispatchers.Main) {
                title = "Photos of ${c.name}"
                binding.rvPhotos.layoutManager = GridLayoutManager(this@FaceMatchResultActivity, 3)
                binding.rvPhotos.adapter = PhotoGridAdapter(p)
                selected.addAll(p.map { it.id })
                updateSendButton()
            }
        }
    }

    private fun updateSendButton() {
        binding.btnSendSelected.text = "Send ${selected.size} photo(s) via WhatsApp"
        binding.btnSendSelected.isEnabled = selected.isNotEmpty()
    }

    private fun sendSelected() {
        val c = contact ?: return
        val uris = photos.filter { it.id in selected }.map { Uri.parse(it.photoUri) }
        WhatsAppSender.sendMultipleImages(this, c.phone, uris, "Hey! Here are some photos of you 📸")
        lifecycleScope.launch(Dispatchers.IO) {
            selected.forEach { db.matchedPhotoDao().markSent(it, System.currentTimeMillis()) }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FaceMatchResultActivity, "Sent via WhatsApp!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    inner class PhotoGridAdapter(private val items: List<MatchedPhoto>) :
        RecyclerView.Adapter<PhotoGridAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val image: ImageView = v.findViewById(android.R.id.icon)
            val check: CheckBox  = v.findViewById(android.R.id.checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.activity_list_item, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = items[pos]
            Glide.with(h.image.context).load(Uri.parse(item.photoUri))
                .centerCrop().into(h.image)
            h.check.isChecked = item.id in selected
            h.itemView.setOnClickListener {
                if (item.id in selected) selected.remove(item.id)
                else selected.add(item.id)
                h.check.isChecked = item.id in selected
                updateSendButton()
            }
        }

        override fun getItemCount() = items.size
    }
}
