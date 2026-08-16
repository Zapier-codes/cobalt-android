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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.findNavController
import com.cobalt.android.download.DownloadService
import com.cobalt.android.ui.DownloadQueueSheet
import com.cobalt.android.ui.DownloadQueueViewModel
import com.cobalt.android.ui.SettingsSheet
import com.cobalt.android.util.ClipboardHelper
import com.cobalt.android.util.SettingsRepository
import com.cobalt.android.util.UrlMatcher
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity(), CobaltWebView.Listener {

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

    // ── WebView setup ──────────────────────────────────────────────────────

    private fun setupWebView() {
        binding.webView.listener = this
        binding.webView.audioOnlyMode = settings.audioOnlyMode
        binding.webView.loadUrl(settings.cobaltInstanceUrl)

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
                sheet.onCobaltUrlChanged = { newUrl ->
                    binding.webView.loadUrl(newUrl)
                }
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

    private fun submitUrl(url: String) {
        currentOriginalUrl = url
        binding.webView.audioOnlyMode = settings.audioOnlyMode
        binding.webView.submitUrl(url, settings.audioOnlyMode)
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

    // ── CobaltWebView.Listener ─────────────────────────────────────────────

    override fun onUrlSubmitted(originalUrl: String) {
        currentOriginalUrl = originalUrl
    }

    override fun onBlobDownloadReady(tempFile: File, filename: String, mimeType: String) {
        Toast.makeText(this, getString(R.string.merging_locally), Toast.LENGTH_SHORT).show()
        DownloadService.startBlob(this, tempFile.absolutePath, filename, mimeType, currentOriginalUrl)
    }

    override fun onBlobError(message: String) {
        Toast.makeText(this, getString(R.string.local_merge_failed), Toast.LENGTH_SHORT).show()
    }

    override fun onPageError(url: String, isCustomInstance: Boolean) {
        val hint = if (isCustomInstance) "<p style='color:#818181;font-size:12px'>${getString(R.string.check_cobalt_url)}</p>" else ""
        val errorHtml = """<!DOCTYPE html>
<html><body style='background:#000;color:#e1e1e1;font-family:monospace;
display:flex;flex-direction:column;align-items:center;justify-content:center;
height:100vh;margin:0;padding:24px;box-sizing:border-box;text-align:center'>
<p style='font-size:16px'>can't reach cobalt</p>
$hint
<button onclick='location.reload()' style='margin-top:16px;background:#191919;
color:#e1e1e1;border:1px solid #383838;border-radius:11px;padding:8px 20px;
font-family:monospace;font-size:14px;cursor:pointer'>retry</button>
</body></html>"""
        binding.webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    override fun onPageLoaded() { /* no-op */ }

    override fun onLocalProcessingDetected() {
        Toast.makeText(this, getString(R.string.merging_locally), Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
