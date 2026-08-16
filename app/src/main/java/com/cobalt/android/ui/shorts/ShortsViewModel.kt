package com.cobalt.android.ui.shorts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ShortsViewModel : ViewModel() {
    private val _shorts = MutableLiveData<List<String>>()
    val shorts: LiveData<List<String>> = _shorts

    init {
        // Provide some dummy short titles for demo; replace with real data fetching
        _shorts.value = listOf("Example Short 1", "Example Short 2", "Example Short 3")
    }
}
