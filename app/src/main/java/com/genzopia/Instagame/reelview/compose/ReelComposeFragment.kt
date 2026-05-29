package com.genzopia.Instagame.reelview.compose

import ReelViewModel
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

/**
 * Fragment wrapper for the Compose-based Reel Screen.
 *
 * Edge-to-edge strategy:
 *  - consumeWindowInsets = false → lets Compose receive raw window insets so
 *    statusBarsPadding() / navigationBarsPadding() modifiers work correctly.
 *  - Negative top margin in onViewCreated pulls the view up behind the status
 *    bar, undoing the top padding MainActivity applies to its root container.
 */
class ReelComposeFragment : Fragment() {

    private val viewModel: ReelViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Init persistent URL-type cache so HEAD probing is skipped on repeat visits
        ReelPagingSource.init(requireContext())

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Let Compose handle all insets itself — don't let the View system
            // consume them before they reach the Compose tree.
            consumeWindowInsets = false

            setContent {
                MaterialTheme {
                    Surface {
                        ReelScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Pull the view up by the status-bar height so the video draws behind it.
        // MainActivity pads its root container by systemBars.top; we undo that
        // here so the reel is truly full-bleed.
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
