package com.cobalt.android.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cobalt.android.link.LinkResolverRepository
import kotlinx.coroutines.launch

/**
 * Phase 4: holds the paste-link field's state and drives a real
 * `LinkResolverRepository.resolve()` network call on submit — no more
 * fixed "not implemented yet" placeholder message (that was correct for
 * Phase 3's scope only; see ARCHITECTURE.md "No stubs, no placeholders").
 * A successful resolve is kept in `resolveResult` for Phase 6's picker UI
 * to consume; this phase doesn't build that UI, it just makes sure the
 * real data is there waiting for it.
 *
 * Moved from a plain `ViewModel` to `AndroidViewModel` because
 * `LinkResolverRepository` needs a `Context` to read `SettingsRepository`
 * (the configured cobalt instance URL) — same pattern already used by
 * `ShortsViewModel` for the same reason.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LinkResolverRepository(application)

    private val _linkInput = MutableLiveData("")
    val linkInput: LiveData<String> = _linkInput

    private val _statusMessage = MutableLiveData<String?>(null)
    val statusMessage: LiveData<String?> = _statusMessage

    private val _isResolving = MutableLiveData(false)
    val isResolving: LiveData<Boolean> = _isResolving

    /** Held here for Phase 6's resolution-picker UI to read; this phase
     * only guarantees it's real, resolved data — not who displays it. */
    private val _resolveResult = MutableLiveData<LinkResolverRepository.ResolveResult?>(null)
    val resolveResult: LiveData<LinkResolverRepository.ResolveResult?> = _resolveResult

    /** Called once, from a `pending_url` nav argument (share intent / clipboard / shortcut). */
    fun setPendingUrl(url: String) {
        _linkInput.value = url
    }

    fun onLinkInputChanged(text: String) {
        _linkInput.value = text
        _statusMessage.value = null
        _resolveResult.value = null
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
        if (_isResolving.value == true) return

        viewModelScope.launch {
            _isResolving.value = true
            _statusMessage.value = "Resolving…"
            _resolveResult.value = null

            when (val result = repository.resolve(url)) {
                is LinkResolverRepository.ResolveResult.Success -> {
                    _resolveResult.value = result
                    _statusMessage.value = "Found ${result.formats.size} format(s)."
                }
                is LinkResolverRepository.ResolveResult.Error -> {
                    _statusMessage.value = result.message
                }
            }

            _isResolving.value = false
        }
    }

    /** Phase 6: called by `ResolutionPickerDialog` once a format is picked
     * (or the sheet is dismissed without picking one), so a stale Success
     * result doesn't cause the sheet to silently re-show on the next
     * observe (e.g. after a config change). */
    fun clearResolveResult() {
        _resolveResult.value = null
    }

    private fun isPlausibleUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
