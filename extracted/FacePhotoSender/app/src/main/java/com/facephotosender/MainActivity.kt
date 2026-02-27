package com.facephotosender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.facephotosender.adapter.ContactCardAdapter
import com.facephotosender.data.Contact
import com.facephotosender.data.MatchedPhoto
import com.facephotosender.databinding.ActivityMainBinding
import com.facephotosender.whatsapp.WhatsAppSender
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ContactCardAdapter

    // Permission launcher
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) startScan()
        else Toast.makeText(this, "Storage permission is required to scan photos", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = ContactCardAdapter(
            onSendClick = { contact, photos -> sendPhotos(contact, photos) },
            onEditClick = { contact ->
                ContactSetupActivity.start(this, contact.id)
            }
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter
    }

    private fun setupButtons() {
        binding.fabAddContact.setOnClickListener {
            ContactSetupActivity.start(this, -1L)
        }

        binding.btnScan.setOnClickListener {
            checkPermissionAndScan()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.contacts.collect { contacts ->
                binding.tvEmptyState.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                binding.rvContacts.visibility   = if (contacts.isEmpty()) View.GONE else View.VISIBLE
                binding.btnScan.isEnabled       = contacts.isNotEmpty()
            }
        }

        lifecycleScope.launch {
            viewModel.scanState.collect { state ->
                when (state) {
                    is ScanState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = ""
                    }
                    is ScanState.Scanning -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.progressBar.max      = state.total
                        binding.progressBar.progress = state.progress
                        binding.tvStatus.text        = "Scanning ${state.progress}/${state.total} photos…"
                    }
                    is ScanState.Done -> {
                        binding.progressBar.visibility = View.GONE
                        val total = state.results.values.sumOf { it.size }
                        binding.tvStatus.text = if (total == 0)
                            "Scan complete – no matches found."
                        else
                            "Scan complete – found $total matching photo(s)."
                        adapter.setResults(state.results)
                    }
                    is ScanState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = state.message
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun checkPermissionAndScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) startScan() else permLauncher.launch(perms)
    }

    private fun startScan() {
        viewModel.scanPhotos()
    }

    private fun sendPhotos(contact: Contact, photos: List<MatchedPhoto>) {
        if (photos.isEmpty()) return
        val uris = photos.map { Uri.parse(it.photoUri) }
        WhatsAppSender.sendMultipleImages(this, contact.phone, uris, "Hey! Here are some photos of you 📸")
        photos.forEach { viewModel.markPhotoSent(it.id) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadContacts()
    }
}
