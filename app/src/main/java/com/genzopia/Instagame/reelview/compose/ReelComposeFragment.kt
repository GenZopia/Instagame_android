package com.genzopia.Instagame.reelview.compose

import ReelViewModel
import android.content.res.ColorStateList
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
 * Fragment wrapper for the Compose-based Reel Screen.
 *
 * Applies Instagram-style AMOLED-black bars on the reel screen.
 * The bottom tab bar stays visible at all times — it gets a black background
 * on the reel and returns to normal elsewhere.
 */
class ReelComposeFragment : Fragment() {

    private val viewModel: ReelViewModel by viewModels()
    private var navOriginalBackground: android.graphics.drawable.Drawable? = null
    private var navOriginalIconTint: ColorStateList? = null
    private var navOriginalTextColor: ColorStateList? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ReelPagingSource.init(requireContext())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
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
        SystemBarUtils.applyReelBars(requireActivity().window)
        styleBottomNavReel()
    }

    override fun onPause() {
        super.onPause()
        SystemBarUtils.restoreDefaultBars(requireActivity().window)
        styleBottomNavNormal()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.let { act ->
            SystemBarUtils.restoreDefaultBars(act.window)
            styleBottomNavNormal()
        }
    }

    private fun styleBottomNavReel() {
        val nav = activity?.findViewById<BottomNavigationView>(R.id.nav_view) ?: return
        if (navOriginalBackground == null) {
            navOriginalBackground = nav.background
            navOriginalIconTint = nav.itemIconTintList
            navOriginalTextColor = nav.itemTextColor
        }
        nav.setBackgroundColor(android.graphics.Color.BLACK)
        nav.itemIconTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        nav.itemTextColor = ColorStateList.valueOf(android.graphics.Color.WHITE)
    }

    private fun styleBottomNavNormal() {
        val nav = activity?.findViewById<BottomNavigationView>(R.id.nav_view) ?: return
        navOriginalBackground?.let { nav.background = it }
        navOriginalIconTint?.let { nav.itemIconTintList = it }
        navOriginalTextColor?.let { nav.itemTextColor = it }
        navOriginalBackground = null
        navOriginalIconTint = null
        navOriginalTextColor = null
    }
}
