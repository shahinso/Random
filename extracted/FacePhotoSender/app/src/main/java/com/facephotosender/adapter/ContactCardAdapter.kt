package com.facephotosender.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.facephotosender.R
import com.facephotosender.data.Contact
import com.facephotosender.data.MatchedPhoto
import com.facephotosender.databinding.ItemContactCardBinding

class ContactCardAdapter(
    private val onSendClick: (Contact, List<MatchedPhoto>) -> Unit,
    private val onEditClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactCardAdapter.ViewHolder>() {

    // items = all contacts; results only populated after scan
    private var contacts: List<Contact> = emptyList()
    private var results: Map<Contact, List<MatchedPhoto>> = emptyMap()

    fun setResults(newResults: Map<Contact, List<MatchedPhoto>>) {
        results = newResults
        contacts = newResults.keys.toList()
        notifyDataSetChanged()
    }

    fun setContacts(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount() = contacts.size

    inner class ViewHolder(private val b: ItemContactCardBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(contact: Contact) {
            b.tvName.text  = contact.name
            b.tvPhone.text = contact.phone

            val photos = results[contact] ?: emptyList()
            b.tvMatchCount.text = if (photos.isEmpty())
                "No matches yet – run a scan"
            else
                "${photos.size} photo(s) found"

            // Thumbnail of first matched photo
            if (photos.isNotEmpty()) {
                b.ivThumbnail.visibility = View.VISIBLE
                Glide.with(b.root.context)
                    .load(Uri.parse(photos.first().photoUri))
                    .centerCrop()
                    .into(b.ivThumbnail)
            } else {
                b.ivThumbnail.visibility = View.GONE
            }

            // Face enrolment indicator
            b.ivFaceEnrolled.visibility =
                if (contact.faceEmbedding != null) View.VISIBLE else View.GONE

            b.btnSend.isEnabled = photos.isNotEmpty()
            b.btnSend.setOnClickListener { onSendClick(contact, photos) }
            b.btnEdit.setOnClickListener { onEditClick(contact) }
        }
    }
}
