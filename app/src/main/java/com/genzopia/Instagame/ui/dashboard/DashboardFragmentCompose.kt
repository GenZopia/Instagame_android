package com.genzopia.Instagame.ui.dashboard

import ReelViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.genzopia.Instagame.R
import com.genzopia.Instagame.common.utils.SystemBarUtils
import com.genzopia.Instagame.onboarding.OnboardingTutorialHost
import com.genzopia.Instagame.reelview.compose.ReelScreen
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Modern Dashboard Fragment using Jetpack Compose.
 * Hosts the reel screen with Instagram-style AMOLED-black bars.
 */
class DashboardFragmentCompose : Fragment() {

    private val viewModel: ReelViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        com.genzopia.Instagame.reelview.compose.ReelPagingSource.init(requireContext())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                // Force dark colour scheme for the reel
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        background = Color.Black,
                        surface = Color(0xFF121212),
                        onBackground = Color.White,
                        onSurface = Color.White
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black
                    ) {
                        OnboardingTutorialHost(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // True-black AMOLED bars + white icons
        SystemBarUtils.applyReelBars(requireActivity().window)
        // Hide bottom nav so reel goes edge-to-edge
        requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        // Restore transparent bars
        SystemBarUtils.restoreDefaultBars(requireActivity().window)
        // Show bottom nav again
        requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.VISIBLE
    }
}
