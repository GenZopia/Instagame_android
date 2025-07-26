// HomeFragment.java
package com.genzopia.Instagame.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.databinding.FragmentHomeBinding;
import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.ImageItem;
import com.genzopia.Instagame.vertical_recylerview_custom.HomeAdapter;
import com.genzopia.Instagame.vertical_recylerview_custom.VideoItem;

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;
import java.util.HashMap;
import java.util.Map;
import android.view.GestureDetector;
import android.view.MotionEvent;
import com.google.android.exoplayer2.Player;


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeAdapter verticalAdapter;
    private String currentTouchedVideoId = null;
    private boolean isLoading = true;
    private Handler handler = new Handler();
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private int currentPlayingPosition = RecyclerView.NO_POSITION;
    private Map<String, Long> videoPositions = new HashMap<>(); // Store video positions

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Set up global ExoPlayer and PlayerView
        exoPlayer = new ExoPlayer.Builder(requireContext()).build();
        playerView = new PlayerView(requireContext());
        playerView.setUseController(false);
        playerView.setPlayer(exoPlayer);
        
        // Enable looping
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);

        // Set up vertical RecyclerView
        RecyclerView verticalRecyclerView = binding.verticalRecyclerView;
        verticalRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Add scroll listener for auto-play and preloading
        verticalRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    handleAutoPlayAndPreload();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // Optionally, handle fast scrolls or continuous updates
            }
        });

        // Set up adapter with loading state
        verticalAdapter = new HomeAdapter(requireContext(), new ArrayList<>(), new ArrayList<>());
        verticalAdapter.setExoPlayer(exoPlayer);
        verticalRecyclerView.setAdapter(verticalAdapter);
        verticalAdapter.setRecyclerView(verticalRecyclerView);
        verticalAdapter.setLoading(true);
        isLoading = true;

        // Simulate loading delay, then set real data
        handler.postDelayed(() -> {
            // Create profile items (only 5 unique profiles)
            List<ImageItem> profileItems = new ArrayList<>();
            profileItems.add(new ImageItem("1", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?ixlib=rb-4.0.3&auto=format&fit=crop&w=880&q=80"));
            profileItems.add(new ImageItem("2", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?ixlib=rb-4.0.3&auto=format&fit=crop&w=2070&q=80"));
            profileItems.add(new ImageItem("3", "https://images.unsplash.com/photo-1526779259212-939e64788e3c?fm=jpg&q=60&w=3000&ixlib=rb-4.1.0"));
            profileItems.add(new ImageItem("4", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?ixlib=rb-4.0.3&auto=format&fit=crop&w=2346&q=80"));
            profileItems.add(new ImageItem("5", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?ixlib=rb-4.0.3&auto=format&fit=crop&w=2340&q=80"));

            // Create video items (4 unique videos)
            List<VideoItem> videoItems = new ArrayList<>();
            videoItems.add(new VideoItem(
                    "1",
                    "Amazing Mountain Landscape",
                    "Nature Channel",
                    "1.2M views",
                    "3 days ago",
                    "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?ixlib=rb-4.0.3&auto=format&fit=crop&w=880&q=80",
                    "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/video/video_4c27b8f7-499c-4e6d-9323-f0591afd58d1.mp4"
            ));

            videoItems.add(new VideoItem(
                    "2",
                    "Sunset at the Beach",
                    "Travel Adventures",
                    "850K views",
                    "1 week ago",
                    "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?ixlib=rb-4.0.3&auto=format&fit=crop&w=2070&q=80",
                    "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/video/video_4c27b8f7-499c-4e6d-9323-f0591afd58d1.mp4"
            ));
            
            videoItems.add(new VideoItem(
                    "3",
                    "Mountain Adventures",
                    "Adventure Time",
                    "2.1M views",
                    "5 days ago",
                    "https://images.unsplash.com/photo-1526779259212-939e64788e3c?fm=jpg&q=60&w=3000&ixlib=rb-4.1.0",
                    "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/video/video_4c27b8f7-499c-4e6d-9323-f0591afd58d1.mp4"
            ));

            videoItems.add(new VideoItem(
                    "4",
                    "Ocean Waves",
                    "Sea Life",
                    "1.5M views",
                    "2 days ago",
                    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?ixlib=rb-4.0.3&auto=format&fit=crop&w=2346&q=80",
                    "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/video/video_4c27b8f7-499c-4e6d-9323-f0591afd58d1.mp4 "
            ));
            // Set real data
            verticalAdapter = new HomeAdapter(requireContext(), profileItems, videoItems);
            verticalAdapter.setExoPlayer(exoPlayer);
            verticalRecyclerView.setAdapter(verticalAdapter);
            verticalAdapter.setRecyclerView(verticalRecyclerView);
            // Preload first 10 videos before hiding shimmer
            handler.postDelayed(() -> {
                verticalAdapter.setLoading(false);
                isLoading = false;
            }, 1200); // Wait for preloading (tune as needed)
        }, 2000); // 2 seconds loading

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
        if (verticalAdapter != null) {
            verticalAdapter.releaseAllPlayers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (verticalAdapter != null) {
            verticalAdapter.releaseAllPlayers();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        playerView = null;
        if (verticalAdapter != null) {
            verticalAdapter.releaseAllPlayers();
            verticalAdapter = null;
        }
        binding = null;
    }

    private void handleAutoPlayAndPreload() {
        if (verticalAdapter == null || binding == null) return;
        RecyclerView recyclerView = binding.verticalRecyclerView;
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        int center = (firstVisible + lastVisible) / 2;
        int playIndex = Math.max(1, center); // skip profile at 0
        
        // Get the video item for this position
        Object item = verticalAdapter.getItem(playIndex);
        if (item instanceof VideoItem) {
            VideoItem videoItem = (VideoItem) item;
            
            // Only switch if new
            if (currentPlayingPosition != playIndex) {
                // Load the video and always start from beginning for now
                exoPlayer.setMediaItem(MediaItem.fromUri(videoItem.videoUrl));
                exoPlayer.prepare();
                
                // Always start from beginning for simple control
                exoPlayer.seekTo(0);
                
                exoPlayer.play(); // Start playing
                verticalAdapter.attachPlayerViewTo(playIndex, playerView);
                currentPlayingPosition = playIndex;
            }
        }
        // Preload logic can remain as before if desired
        verticalAdapter.preloadAround(playIndex);
    }
}