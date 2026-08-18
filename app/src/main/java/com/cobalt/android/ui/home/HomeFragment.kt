package com.cobalt.android.ui.home

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.widget.addTextChangedListener
import com.cobalt.android.databinding.FragmentHomeBinding
import com.cobalt.android.link.LinkResolverRepository
import com.cobalt.android.ui.downloads.ResolutionPickerDialog
import com.cobalt.android.ui.widget.SkeletonPulse

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Phase 17: only running while skeletonResolving is visible; cancelled
    // (not just hidden) the moment resolving ends, per SkeletonPulse's own
    // lifecycle contract.
    private var skeletonAnimator: ValueAnimator? = null

    // Activity-scoped (not viewModels()) so ResolutionPickerDialog, shown via
    // childFragmentManager, reads the same HomeViewModel instance/resolveResult
    // rather than getting its own separate one.
    private val viewModel: HomeViewModel by activityViewModels()

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

        // Phase 4: disable the submit button and field while a real
        // network resolve is in flight, so a slow/unreachable instance
        // can't be double-submitted.
        viewModel.isResolving.observe(viewLifecycleOwner) { resolving ->
            binding.btnSubmit.isEnabled = !resolving
            binding.etLinkInput.isEnabled = !resolving

            // Phase 17: skeleton placeholder for the resolve-in-flight state.
            binding.skeletonResolving.visibility = if (resolving) View.VISIBLE else View.GONE
            if (resolving) {
                skeletonAnimator?.cancel()
                skeletonAnimator = SkeletonPulse.start(binding.skeletonResolving)
            } else {
                skeletonAnimator?.cancel()
                skeletonAnimator = null
            }
        }

        // Phase 6: show the resolution picker once a link resolves.
        // childFragmentManager (not parentFragmentManager) so the sheet's
        // lifecycle is tied to this fragment, not the hosting activity.
        viewModel.resolveResult.observe(viewLifecycleOwner) { result ->
            if (result is LinkResolverRepository.ResolveResult.Success &&
                childFragmentManager.findFragmentByTag(ResolutionPickerDialog.TAG) == null
            ) {
                ResolutionPickerDialog.newInstance().show(childFragmentManager, ResolutionPickerDialog.TAG)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        _binding = null
    }
}
