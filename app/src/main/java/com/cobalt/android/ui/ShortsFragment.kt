package com.cobalt.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cobalt.android.databinding.FragmentShortsBinding

class ShortsFragment : Fragment() {
    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentShortsBinding.inflate(inflater, container, false)
        val view = binding.root
        val viewModel = ViewModelProvider(this).get(ShortsViewModel::class.java)
        // Observe shorts data and set up adapter
        viewModel.shorts.observe(viewLifecycleOwner) { list ->
            binding.vpShorts.adapter = ShortsAdapter(list)
        }
        // Provide initial data if no observer yet
        binding.vpShorts.adapter = ShortsAdapter(listOf("Loading..."))
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
