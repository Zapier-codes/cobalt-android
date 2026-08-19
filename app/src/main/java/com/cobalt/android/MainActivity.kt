package com.cobalt.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.cobalt.android.databinding.ActivityMainBinding
import com.cobalt.android.ui.DownloadQueueSheet
import com.cobalt.android.ui.DownloadQueueViewModel
import com.cobalt.android.ui.SettingsSheet
import com.cobalt.android.util.ClipboardHelper
import com.cobalt.android.util.SettingsRepository
import com.cobalt.android.util.UrlMatcher
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private val queueViewModel: DownloadQueueViewModel by viewModels()
    private var currentOriginalUrl: String = ""

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — app works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)

        // Set up navigation for bottom navigation bar
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.navBottom)
            .setupWithNavController(findNavController(R.id.navHost))
        setupFab()
        setupSettingsButton()
        observeDownloadQueue()
        handleFirstLaunch()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (settings.clipboardTriggerEnabled) checkClipboard()
    }

    // ── Download queue badge ─────────────────────────────────────────────────
    // Was previously only wired up inside a dead setupWebView() that onCreate()
    // never called, so the badge never actually updated. Moved here so it runs.

    private fun observeDownloadQueue() {
        queueViewModel.activeDownloads.observe(this) { list ->
            val count = list.size
            if (count > 0) {
                binding.tvBadge.visibility = View.VISIBLE
                binding.tvBadge.text = count.toString()
            } else {
                binding.tvBadge.visibility = View.GONE
            }
        }
    }

    private fun setupFab() {
        binding.fabQueue.setOnClickListener {
            DownloadQueueSheet.newInstance().also { sheet ->
                sheet.onRetry = { record -> submitUrl(record.originalUrl) }
                sheet.show(supportFragmentManager, DownloadQueueSheet.TAG)
            }
        }
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            SettingsSheet.newInstance().also { sheet ->
                sheet.show(supportFragmentManager, SettingsSheet.TAG)
            }
        }
    }

    // ── Intent handling ────────────────────────────────────────────────────

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = UrlMatcher.extractUrl(text)
                if (url != null) {
                    submitUrl(url)
                } else {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.not_a_supported_link),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            // Static shortcut: Open Queue
            intent?.getStringExtra("shortcut_queue") == "true" ||
            intent?.getBooleanExtra("shortcut_queue", false) == true -> {
                binding.fabQueue.performClick()
            }
            // Static shortcut: Paste & Download
            intent?.getStringExtra("shortcut_paste") == "true" ||
            intent?.getBooleanExtra("shortcut_paste", false) == true -> {
                val url = ClipboardHelper.getSupportedUrl(this)
                if (url != null) submitUrl(url)
            }
        }
    }

    // ── Clipboard trigger ──────────────────────────────────────────────────

    private fun checkClipboard() {
        val url = ClipboardHelper.getSupportedUrl(this) ?: return
        Snackbar.make(binding.root, getString(R.string.download_from_clipboard), Snackbar.LENGTH_LONG)
            .setAction("download") { submitUrl(url) }
            .setBackgroundTint(getColor(R.color.cobalt_surface))
            .setTextColor(getColor(R.color.cobalt_text_primary))
            .setActionTextColor(getColor(R.color.cobalt_accent_blue))
            .show()
    }

    // ── URL routing (no WebView) ─────────────────────────────────────────────
    // Previously handed the URL to a WebView + JS bridge to resolve. That's
    // gone. This now routes the URL to the Home tab as a nav argument;
    // HomeFragment (Phase 3) is responsible for actually resolving it via
    // LinkResolverRepository and driving the download from there. This is
    // real navigation, not a stub — it does not pretend to resolve the link
    // itself.

    private fun submitUrl(url: String) {
        currentOriginalUrl = url
        findNavController(R.id.navHost).navigate(
            R.id.nav_home,
            bundleOf("pending_url" to url)
        )
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.navBottom)
            .selectedItemId = R.id.nav_home
    }

    // ── First launch ───────────────────────────────────────────────────────

    private fun handleFirstLaunch() {
        if (settings.firstLaunchDone) return
        settings.firstLaunchDone = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.battery_dialog_title))
                .setMessage(getString(R.string.battery_dialog_message))
                .setPositiveButton(getString(R.string.allow)) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
                .setNegativeButton(getString(R.string.not_now), null)
                .show()
        }
    }
}
