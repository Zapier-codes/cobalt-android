package com.cobalt.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.cobalt.android.databinding.SheetSettingsBinding
import com.cobalt.android.ui.history.HistoryFragment
import com.cobalt.android.util.SettingsRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadQueueViewModel by activityViewModels()
    private lateinit var settings: SettingsRepository

    // The instance URL used to be a manual field here (see git history);
    // it's now centrally managed via RemoteConfigRepository's
    // remote-config.json, not per-device — see that class's doc comment.
    // `onCobaltUrlChanged` had no subscriber (verified before removing —
    // grepped for `SettingsSheet(` construction sites and the callback
    // itself; nothing outside this file ever read it), so it's gone too,
    // not just its call site.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings = SettingsRepository(requireContext())

        binding.etCobaltApiKey.setText(settings.cobaltApiKey)
        binding.switchAudioOnly.isChecked = settings.audioOnlyMode
        binding.switchClipboard.isChecked = settings.clipboardTriggerEnabled

        binding.switchAudioOnly.setOnCheckedChangeListener { _, checked ->
            settings.audioOnlyMode = checked
        }
        binding.switchClipboard.setOnCheckedChangeListener { _, checked ->
            settings.clipboardTriggerEnabled = checked
        }
        binding.btnBattery.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
            )
        }
        binding.btnHistory.setOnClickListener {
            HistoryFragment.newInstance().show(parentFragmentManager, HistoryFragment.TAG)
        }
        binding.btnClearHistory.setOnClickListener {
            viewModel.clearHistory()
            dismiss()
        }
    }

    override fun onStop() {
        super.onStop()
        // Unlike the old URL field, blank here is a valid, meaningful value
        // ("stop sending an API key") — always write, don't skip on blank.
        val apiKey = _binding?.etCobaltApiKey?.text?.toString()?.trim().orEmpty()
        if (apiKey != settings.cobaltApiKey) {
            settings.cobaltApiKey = apiKey
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsSheet"
        fun newInstance() = SettingsSheet()
    }
}
