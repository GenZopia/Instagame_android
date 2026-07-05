package com.genzopia.Instagame.ui.dashboard

import com.genzopia.Instagame.reelview.compose.ReelViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.genzopia.Instagame.onboarding.OnboardingTutorialHost
import com.genzopia.Instagame.reelview.compose.ReelPagingSource

/**
 * Modern Dashboard Fragment using Jetpack Compose.
 * Replaces the old RecyclerView-based implementation.
 *
 * Edge-to-edge strategy: same as ReelComposeFragment — negative top margin
 * pulls the view behind the status bar; Compose overlays use
 * statusBarsPadding() / navigationBarsPadding() to stay clear of system bars.
 */
class DashboardFragmentCompose : Fragment() {

    private val viewModel: ReelViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Init persistent URL-type cache (same as ReelComposeFragment)
        ReelPagingSource.init(requireContext())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Let Compose handle all insets itself.
            consumeWindowInsets = false

            setContent {
                MaterialTheme {
                    Surface {
                        OnboardingTutorialHost(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Pull the view up by the status-bar height so the reel draws behind it.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarHeight = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars()
            ).top
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
            lp?.topMargin = -statusBarHeight
            v.layoutParams = lp
            insets
        }
    }
}
