package com.cobalt.android.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Phase 3 scope only: holds the paste-link field's state and reacts to a
 * submit tap with an honest "not implemented yet" status. There is no
 * `LinkResolverRepository` call here — that's Phase 4. This is deliberately
 * NOT a fake network call (e.g. a fixed delay then a canned "success") —
 * see ARCHITECTURE.md "No stubs, no placeholders". Once Phase 4 adds the
 * real repository, `onSubmit` below is where that call gets wired in.
 */
class HomeViewModel : ViewModel() {

    private val _linkInput = MutableLiveData("")
    val linkInput: LiveData<String> = _linkInput

    private val _statusMessage = MutableLiveData<String?>(null)
    val statusMessage: LiveData<String?> = _statusMessage

    /** Called once, from a `pending_url` nav argument (share intent / clipboard / shortcut). */
    fun setPendingUrl(url: String) {
        _linkInput.value = url
    }

    fun onLinkInputChanged(text: String) {
        _linkInput.value = text
        _statusMessage.value = null
    }

    fun onSubmit() {
        val url = _linkInput.value.orEmpty().trim()
        if (url.isEmpty()) {
            _statusMessage.value = "Paste a link first."
            return
        }
        if (!isPlausibleUrl(url)) {
            _statusMessage.value = "That doesn't look like a valid link."
            return
        }
        // Real status, not a fake success: link resolution isn't wired up
        // until Phase 4 (LinkResolverRepository). This is scaffolding being
        // honest about its own incompleteness, not a stub pretending to work.
        _statusMessage.value = "Link resolution isn't implemented yet (Phase 4)."
    }

    private fun isPlausibleUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
