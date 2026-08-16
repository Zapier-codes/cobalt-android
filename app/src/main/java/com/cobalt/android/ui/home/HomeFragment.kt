package com.cobalt.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.widget.addTextChangedListener
import com.cobalt.android.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set once per arrival at this destination — MainActivity.submitUrl()
        // navigates here fresh with a new "pending_url" each time, so there's
        // no need to persist/consume-once logic beyond reading it on create.
        arguments?.getString("pending_url")?.let { url ->
            viewModel.setPendingUrl(url)
        }

        binding.etLinkInput.addTextChangedListener(
            onTextChanged = { text, _, _, _ -> viewModel.onLinkInputChanged(text?.toString().orEmpty()) }
        )

        binding.btnSubmit.setOnClickListener { viewModel.onSubmit() }

        viewModel.linkInput.observe(viewLifecycleOwner) { url ->
            if (binding.etLinkInput.text?.toString() != url) {
                binding.etLinkInput.setText(url)
                binding.etLinkInput.setSelection(url.length)
            }
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            binding.tvStatus.text = message
            binding.tvStatus.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
