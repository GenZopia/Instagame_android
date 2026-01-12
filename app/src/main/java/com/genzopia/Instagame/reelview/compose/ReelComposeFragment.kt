package com.genzopia.Instagame.reelview.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

/**
 * Fragment wrapper for the Compose-based Reel Screen
 * This allows integration with existing Fragment-based navigation
 */
class ReelComposeFragment : Fragment() {
    
    private val viewModel: ReelViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                MaterialTheme {
                    Surface {
                        ReelScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
