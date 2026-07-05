package com.genzopia.Instagame.ui.home

import com.genzopia.Instagame.features.home.ui.HomeViewModel
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

    override fun onResume() {
        super.onResume()
        // Check for pending game deep link
        val gameId = com.genzopia.Instagame.utils.GameNavigationManager.getInstance()
            .consumePendingGameId()
        if (gameId != null && gameId.isNotEmpty()) {
            viewModel.openGameByIdFromDeepLink(gameId)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
