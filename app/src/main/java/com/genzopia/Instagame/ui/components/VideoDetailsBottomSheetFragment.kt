package com.genzopia.Instagame.ui.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Fragment that hosts the Compose-based [VideoDetailsBottomSheet].
 * All video data is passed directly via arguments — no Firebase loading inside the sheet.
 */
class VideoDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_VIDEO_ID = "video_id"
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_VIEW_COUNT = "view_count"
        private const val ARG_LIKE_COUNT = "like_count"
        private const val ARG_SHARE_COUNT = "share_count"
        private const val ARG_UPLOAD_DATE = "upload_date"
        private const val ARG_GAME_ID = "game_id"
        private const val ARG_GAME_NAME = "game_name"
        private const val ARG_CHANNEL_NAME = "channel_name"
        private const val ARG_DEVELOPER_ID = "developer_id"

        fun newInstance(
            videoId: String,
            title: String,
            description: String,
            viewCount: String = "0",
            likeCount: String = "0",
            shareCount: String = "0",
            uploadDate: String = "",
            gameId: String = "",
            gameName: String = "",
            channelName: String = "",
            developerId: String = ""
        ): VideoDetailsBottomSheetFragment {
            return VideoDetailsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_ID, videoId)
                    putString(ARG_TITLE, title)
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_VIEW_COUNT, viewCount)
                    putString(ARG_LIKE_COUNT, likeCount)
                    putString(ARG_SHARE_COUNT, shareCount)
                    putString(ARG_UPLOAD_DATE, uploadDate)
                    putString(ARG_GAME_ID, gameId)
                    putString(ARG_GAME_NAME, gameName)
                    putString(ARG_CHANNEL_NAME, channelName)
                    putString(ARG_DEVELOPER_ID, developerId)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val args = arguments ?: Bundle.EMPTY
                VideoDetailsBottomSheet(
                    videoId = args.getString(ARG_VIDEO_ID, ""),
                    title = args.getString(ARG_TITLE, ""),
                    description = args.getString(ARG_DESCRIPTION, ""),
                    viewCount = args.getString(ARG_VIEW_COUNT, "0"),
                    likeCount = args.getString(ARG_LIKE_COUNT, "0"),
                    shareCount = args.getString(ARG_SHARE_COUNT, "0"),
                    uploadDate = args.getString(ARG_UPLOAD_DATE, ""),
                    gameId = args.getString(ARG_GAME_ID, ""),
                    gameName = args.getString(ARG_GAME_NAME, ""),
                    channelName = args.getString(ARG_CHANNEL_NAME, ""),
                    developerId = args.getString(ARG_DEVELOPER_ID, ""),
                    onDismiss = { dismiss() }
                )
            }
        }
    }
}
