package com.genzopia.Instagame.ui.home

import com.genzopia.Instagame.features.home.ui.HomeViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.genzopia.Instagame.ui.home.compose.HomeScreen
import com.genzopia.Instagame.utils.FCMTokenManager
import com.genzopia.Instagame.utils.NotificationPermissionManager

/**
 * Modern Home Fragment using Jetpack Compose
 * Uses activityViewModels so the ViewModel (and its paging cache) survives
 * fragment navigation — no reload when switching tabs and coming back.
 */
class HomeFragmentCompose : Fragment() {

    private val viewModel: HomeViewModel by activityViewModels()
    private lateinit var notificationPermissionManager: NotificationPermissionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        notificationPermissionManager = NotificationPermissionManager(requireContext())
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
        // Req 3.2/3.4: Re-request notification permission if 30 days have passed since last rejection
        if (notificationPermissionManager.shouldRequestPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            notificationPermissionManager.handlePermissionResult(granted)
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(), Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationPermissionManager.markPermanentlyDenied()
                }
            }
            if (granted) {
                FCMTokenManager.registerToken(requireContext())
            }
        }
    }
}
