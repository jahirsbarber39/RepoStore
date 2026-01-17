package com.samyak.repostore.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.facebook.shimmer.ShimmerFrameLayout
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.model.AppCategory
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.databinding.FragmentGameBinding
import com.samyak.repostore.databinding.SectionAppCarouselBinding
import com.samyak.repostore.databinding.SectionAppListBinding
import com.samyak.repostore.ui.activity.AppListActivity
import com.samyak.repostore.ui.activity.DetailActivity
import com.samyak.repostore.ui.adapter.FeaturedAppAdapter
import com.samyak.repostore.ui.adapter.PlayStoreAppAdapter
import com.samyak.repostore.ui.viewmodel.GameUiState
import com.samyak.repostore.ui.viewmodel.GameViewModel
import com.samyak.repostore.ui.viewmodel.GameViewModelFactory
import com.samyak.repostore.ui.viewmodel.ListType
import com.samyak.repostore.util.RateLimitDialog
import kotlinx.coroutines.launch
import kotlin.math.abs

class GameFragment : Fragment() {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory((requireActivity().application as RepoStoreApp).repository)
    }

    private lateinit var featuredAdapter: FeaturedAppAdapter
    private lateinit var popularAdapter: PlayStoreAppAdapter
    private lateinit var newAdapter: PlayStoreAppAdapter

    private lateinit var sectionFeatured: SectionAppCarouselBinding
    private lateinit var sectionPopular: SectionAppListBinding
    private lateinit var sectionNew: SectionAppListBinding

    private val indicators = mutableListOf<ImageView>()
    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize shimmer layout
        shimmerLayout = view.findViewById(R.id.skeleton_layout)

        bindSections()
        setupFeaturedCarousel()
        setupGameSections()
        setupSeeMoreButtons()
        setupSwipeRefresh()
        setupErrorRetry()
        observeViewModel()
    }

    private fun bindSections() {
        sectionFeatured = SectionAppCarouselBinding.bind(binding.sectionFeatured.root)
        sectionPopular = SectionAppListBinding.bind(binding.sectionPopular.root)
        sectionNew = SectionAppListBinding.bind(binding.sectionNew.root)

        sectionFeatured.tvSectionTitle.text = getString(R.string.featured_games)
        sectionPopular.tvSectionTitle.text = getString(R.string.popular_games)
        sectionNew.tvSectionTitle.text = getString(R.string.new_games)
    }

    private fun setupFeaturedCarousel() {
        featuredAdapter = FeaturedAppAdapter { appItem ->
            navigateToDetail(appItem)
        }

        sectionFeatured.viewpagerFeatured.apply {
            adapter = featuredAdapter
            offscreenPageLimit = 3
            clipToPadding = false
            clipChildren = false

            val transformer = CompositePageTransformer()
            transformer.addTransformer(MarginPageTransformer(24))
            transformer.addTransformer { page, position ->
                val scale = 1 - abs(position) * 0.1f
                page.scaleY = scale
            }
            setPageTransformer(transformer)

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIndicators(position)
                }
            })
        }
    }

    private fun setupIndicators(count: Int) {
        sectionFeatured.indicatorContainer.removeAllViews()
        indicators.clear()

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = 6
            marginEnd = 6
        }

        for (i in 0 until count) {
            val indicator = ImageView(requireContext()).apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (i == 0) R.drawable.indicator_active else R.drawable.indicator_inactive
                    )
                )
                layoutParams = params
            }
            indicators.add(indicator)
            sectionFeatured.indicatorContainer.addView(indicator)
        }
    }

    private fun updateIndicators(position: Int) {
        indicators.forEachIndexed { index, imageView ->
            imageView.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    if (index == position) R.drawable.indicator_active else R.drawable.indicator_inactive
                )
            )
        }
    }

    private fun setupGameSections() {
        popularAdapter = PlayStoreAppAdapter { appItem ->
            navigateToDetail(appItem)
        }
        sectionPopular.rvApps.apply {
            adapter = popularAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        newAdapter = PlayStoreAppAdapter { appItem ->
            navigateToDetail(appItem)
        }
        sectionNew.rvApps.apply {
            adapter = newAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupSeeMoreButtons() {
        sectionFeatured.btnSeeMore.setOnClickListener {
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.FEATURED,
                getString(R.string.featured_games),
                AppCategory.GAMES.name
            )
            startActivity(intent)
        }

        sectionPopular.btnSeeMore.setOnClickListener {
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.TRENDING,
                getString(R.string.popular_games),
                AppCategory.GAMES.name
            )
            startActivity(intent)
        }

        sectionNew.btnSeeMore.setOnClickListener {
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.UPDATED,
                getString(R.string.new_games),
                AppCategory.GAMES.name
            )
            startActivity(intent)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupErrorRetry() {
        binding.tvError.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }
    }

    private fun handleUiState(state: GameUiState) {
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            is GameUiState.Loading -> {
                showSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }

            is GameUiState.Empty -> {
                hideSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = getString(R.string.no_games_found)
            }

            is GameUiState.LoadingMore -> {
                hideSkeleton()
                binding.scrollView.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                updateSections(state.currentGames)
            }

            is GameUiState.Success -> {
                hideSkeleton()
                binding.scrollView.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                updateSections(state.games)
            }

            is GameUiState.Error -> {
                hideSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "${state.message}\n\n${getString(R.string.tap_to_retry)}"
                
                // Show rate limit dialog if applicable
                RateLimitDialog.showIfNeeded(requireContext(), state.message)
            }
        }
    }
    
    /**
     * Show skeleton loading animation
     */
    private fun showSkeleton() {
        shimmerLayout?.apply {
            visibility = View.VISIBLE
            startShimmer()
        }
    }
    
    /**
     * Hide skeleton loading animation
     */
    private fun hideSkeleton() {
        shimmerLayout?.apply {
            stopShimmer()
            visibility = View.GONE
        }
    }

    private fun updateSections(games: List<AppItem>) {
        if (games.isEmpty()) return

        // Featured: top 5 by stars
        val featured = games.sortedByDescending { it.repo.stars }.take(5)
        featuredAdapter.submitList(featured)
        setupIndicators(featured.size)

        // Popular: next 10 by stars
        val popular = games.sortedByDescending { it.repo.stars }.drop(5).take(10)
        popularAdapter.submitList(popular.ifEmpty { games.take(10) })

        // New: sorted by creation date
        val newGames = games.sortedByDescending { it.repo.createdAt }.take(10)
        newAdapter.submitList(newGames)
    }

    private fun navigateToDetail(appItem: AppItem) {
        val intent = DetailActivity.newIntent(
            requireContext(),
            appItem.repo.owner.login,
            appItem.repo.name
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shimmerLayout = null
        _binding = null
    }

    companion object {
        fun newInstance() = GameFragment()
    }
}
