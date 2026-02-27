package com.facephotosender.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object WhatsAppSender {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"

    /**
     * Send a single image to a WhatsApp contact identified by [phoneNumber].
     *
     * [phoneNumber] must be in E.164 format, e.g. "+14155552671" (no spaces, dashes, parentheses).
     * [imageUri]   content:// or file:// URI of the image.
     *
     * Two strategies:
     *  1. wa.me deep-link  → opens WhatsApp chat for that number; user taps attachment manually.
     *     This works even without WhatsApp installed (browser fallback).
     *  2. ACTION_SEND intent with EXTRA_STREAM → opens WhatsApp share sheet pre-filled with image.
     *     Preferred when WhatsApp IS installed.
     */
    fun sendImageToContact(
        context: Context,
        phoneNumber: String,
        imageUri: Uri,
        caption: String = ""
    ) {
        if (isWhatsAppInstalled(context)) {
            sendViaIntent(context, phoneNumber, imageUri, caption)
        } else {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Send multiple images to a WhatsApp contact.
     * Uses ACTION_SEND_MULTIPLE.
     */
    fun sendMultipleImages(
        context: Context,
        phoneNumber: String,
        imageUris: List<Uri>,
        caption: String = ""
    ) {
        if (!isWhatsAppInstalled(context)) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_LONG).show()
            return
        }

        if (imageUris.size == 1) {
            sendViaIntent(context, phoneNumber, imageUris.first(), caption)
            return
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            `package` = WHATSAPP_PACKAGE
            putExtra(Intent.EXTRA_TEXT, caption)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: send one-by-one
            imageUris.forEach { uri -> sendViaIntent(context, phoneNumber, uri, caption) }
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun sendViaIntent(
        context: Context,
        phoneNumber: String,
        imageUri: Uri,
        caption: String
    ) {
        // Strip non-numeric except leading +
        val cleanPhone = phoneNumber.trim()

        // Try direct phone-number routing via wa.me URI first (more reliable targeting)
        val waUri = Uri.parse("https://wa.me/${cleanPhone.removePrefix("+")}?text=${Uri.encode(caption)}")

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            `package` = WHATSAPP_PACKAGE
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            putExtra("jid", "${cleanPhone.removePrefix("+")}@s.whatsapp.net")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            // Final fallback: open WhatsApp web / app without pre-filling
            val fallback = Intent(Intent.ACTION_VIEW, waUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    private fun isWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
