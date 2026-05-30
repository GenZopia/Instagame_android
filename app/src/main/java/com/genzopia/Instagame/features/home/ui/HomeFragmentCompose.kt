package com.genzopia.Instagame.ui.home

import HomeViewModel
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.genzopia.Instagame.ui.home.compose.HomeScreen

/**
 * Modern Home Fragment using Jetpack Compose
 * Uses activityViewModels so the ViewModel (and its paging cache) survives
 * fragment navigation — no reload when switching tabs and coming back.
 */
class HomeFragmentCompose : Fragment() {

    // activityViewModels scopes to the Activity — survives fragment back-stack navigation
    private val viewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        com.genzopia.Instagame.analytics.InstagameAnalytics.trackHomeScreenViewed(
            followedUsersCount = viewModel.followedUsers.value.size,
            gamesCount = viewModel.games.value.size
        )
        com.genzopia.Instagame.analytics.SessionTracker.onScreenChanged("home")
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                MaterialTheme {
                    Surface {
                        HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
