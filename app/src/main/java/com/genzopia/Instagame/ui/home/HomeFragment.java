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
import com.google.android.exoplayer2.PlaybackException;


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
            if (!isAdded() || isDetached()) {
                return;
            }
            
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
                    "Amazing Sunset",
                    "Nature Channel",
                    "3.2M views",
                    "1 week ago",
                    "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?ixlib=rb-4.0.3&auto=format&fit=crop&w=880&q=80",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    "Experience the breathtaking beauty of nature's most spectacular moments. This video captures the essence of tranquility and wonder."
            ));

            videoItems.add(new VideoItem(
                    "2",
                    "City Lights",
                    "Urban Life",
                    "1.8M views",
                    "3 days ago",
                    "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?ixlib=rb-4.0.3&auto=format&fit=crop&w=2070&q=80",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    "Discover the vibrant energy of city life through stunning urban photography and captivating visuals."
            ));

            videoItems.add(new VideoItem(
                    "3",
                    "Mountain Adventures",
                    "Adventure Time",
                    "2.1M views",
                    "5 days ago",
                    "https://images.unsplash.com/photo-1526779259212-939e64788e3c?fm=jpg&q=60&w=3000&ixlib=rb-4.1.0",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "Join us on an epic journey through majestic mountain ranges and thrilling outdoor adventures."
            ));

            videoItems.add(new VideoItem(
                    "4",
                    "Ocean Waves",
                    "Sea Life",
                    "1.5M views",
                    "2 days ago",
                    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?ixlib=rb-4.0.3&auto=format&fit=crop&w=2346&q=80",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    "Immerse yourself in the calming rhythm of ocean waves and the mysterious depths of marine life."
            ));
            
            // Preload videos to show thumbnails at 1 second
            preloadVideos(videoItems);
            
            // Wait a bit for preloaded players to be ready
            handler.postDelayed(() -> {
                if (!isAdded() || isDetached()) {
                    return;
                }
                
                // Set real data
                verticalAdapter = new HomeAdapter(requireContext(), profileItems, videoItems);
                verticalAdapter.setExoPlayer(exoPlayer);
                verticalRecyclerView.setAdapter(verticalAdapter);
                verticalAdapter.setRecyclerView(verticalRecyclerView);
                
                // Preload first 10 videos before hiding shimmer
                handler.postDelayed(() -> {
                    if (verticalAdapter != null && isAdded() && !isDetached()) {
                        verticalAdapter.setLoading(false);
                        isLoading = false;
                    }
                }, 1200); // Wait for preloading (tune as needed)
            }, 1000); // Wait 1 second for preloaded players to be ready
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
        // Cancel any pending handler tasks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
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
        if (verticalAdapter == null || binding == null || exoPlayer == null) {
            return;
        }
        
        RecyclerView recyclerView = binding.verticalRecyclerView;
        if (recyclerView == null) {
            return;
        }
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        int center = (firstVisible + lastVisible) / 2;
        int playIndex = Math.max(1, center); // skip profile at 0
        
        // Check if playIndex is valid
        if (playIndex >= verticalAdapter.getItemCount()) {
            return;
        }
        
        // Get the video item for this position
        Object item = verticalAdapter.getItem(playIndex);
        if (item instanceof VideoItem) {
            VideoItem videoItem = (VideoItem) item;
            
            // Save current video position before switching
            if (currentPlayingPosition != RecyclerView.NO_POSITION && currentPlayingPosition < verticalAdapter.getItemCount()) {
                Object currentItem = verticalAdapter.getItem(currentPlayingPosition);
                if (currentItem instanceof VideoItem) {
                    VideoItem currentVideo = (VideoItem) currentItem;
                    long currentPosition = exoPlayer.getCurrentPosition();
                    videoPositions.put(currentVideo.id, currentPosition);
                    
                    // Refresh preloaded thumbnails to show updated positions
                    if (verticalAdapter != null) {
                        refreshPreloadedThumbnails(verticalAdapter.getVideoItems());
                        verticalAdapter.refreshAllVisibleThumbnails();
                    }
                }
            }
            
            // Only switch if new
            if (currentPlayingPosition != playIndex) {
                // Load the video and resume from saved position or start from beginning
                exoPlayer.setMediaItem(MediaItem.fromUri(videoItem.videoUrl));
                exoPlayer.prepare();
                
                // Resume from saved position or start from beginning
                Long savedPosition = videoPositions.get(videoItem.id);
                if (savedPosition != null && savedPosition > 0) {
                    exoPlayer.seekTo(savedPosition);
                } else {
                    exoPlayer.seekTo(0);
                }
                
                exoPlayer.play(); // Start playing
                if (verticalAdapter != null && playerView != null) {
                    verticalAdapter.attachPlayerViewTo(playIndex, playerView);
                }
                currentPlayingPosition = playIndex;
            }
        }
        // Preload logic can remain as before if desired
        if (verticalAdapter != null) {
            verticalAdapter.preloadAround(playIndex);
        }
    }

    private void preloadVideos(List<VideoItem> videoItems) {
        if (videoItems == null || videoItems.isEmpty()) return;
        
        // Preload first 5 videos to show thumbnails
        int preloadCount = Math.min(5, videoItems.size());
        for (int i = 0; i < preloadCount; i++) {
            final VideoItem videoItem = videoItems.get(i);
            
            // Create a separate ExoPlayer for preloading
            ExoPlayer preloadPlayer = new ExoPlayer.Builder(requireContext()).build();
            preloadPlayer.setMediaItem(MediaItem.fromUri(videoItem.videoUrl));
            preloadPlayer.prepare();
            
            // Seek to 1 second and pause to get the thumbnail frame
            preloadPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        // Check if we have a saved position for this video
                        Long savedPosition = videoPositions.get(videoItem.id);
                        if (savedPosition != null && savedPosition > 0) {
                            // Use saved position for thumbnail
                            preloadPlayer.seekTo(savedPosition);
                        } else {
                            // Use 1 second as default
                            preloadPlayer.seekTo(1000); // 1 second = 1000ms
                        }
                        preloadPlayer.setPlayWhenReady(false); // Pause immediately
                    }
                }
                
                @Override
                public void onPlayerError(PlaybackException error) {
                    // Log.e("HomeFragment", "Preload player error for video " + videoItem.id + ": " + error.getMessage());
                }
            });
            
            // Store the preloaded player for later use
            videoItem.preloadedPlayer = preloadPlayer;
        }
    }

    private void refreshPreloadedThumbnails(List<VideoItem> videoItems) {
        if (videoItems == null || videoItems.isEmpty()) return;
        
        // Refresh thumbnails for first 5 videos
        int refreshCount = Math.min(5, videoItems.size());
        for (int i = 0; i < refreshCount; i++) {
            VideoItem videoItem = videoItems.get(i);
            
            if (videoItem.preloadedPlayer != null) {
                // Check if we have a saved position for this video
                Long savedPosition = videoPositions.get(videoItem.id);
                if (savedPosition != null && savedPosition > 0) {
                    // Update thumbnail to saved position
                    videoItem.preloadedPlayer.seekTo(savedPosition);
                    videoItem.preloadedPlayer.setPlayWhenReady(false); // Ensure it's paused
                } else {
                    // Use 1 second as default
                    videoItem.preloadedPlayer.seekTo(1000);
                    videoItem.preloadedPlayer.setPlayWhenReady(false); // Ensure it's paused
                }
            }
        }
    }
}