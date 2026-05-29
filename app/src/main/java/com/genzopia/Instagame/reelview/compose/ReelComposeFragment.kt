package com.genzopia.Instagame.reelview.compose

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
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Fragment wrapper for the Compose-based Reel Screen
 * Integrates with existing Fragment-based navigation.
 *
 * Manages system bars to achieve an Instagram-style AMOLED-black reel
 * experience — true-black status/nav bars with white icons, bottom nav hidden.
 */
class ReelComposeFragment : Fragment() {

    private val viewModel: ReelViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ReelPagingSource.init(requireContext())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                // Force dark colour scheme so the reel is always in dark mode
                // regardless of the system theme.
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
                        ReelScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // True-black AMOLED bars + white icons (like Instagram reels)
        SystemBarUtils.applyReelBars(requireActivity().window)
        // Hide the bottom navigation so the reel goes edge-to-edge
        requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        // Restore transparent bars and default icon colours
        SystemBarUtils.restoreDefaultBars(requireActivity().window)
        // Show the bottom navigation again
        requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)?.visibility = View.VISIBLE
    }
}
