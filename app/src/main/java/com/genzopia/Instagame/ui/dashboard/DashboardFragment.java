package com.genzopia.Instagame.ui.dashboard;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.BuildConfig;
import com.genzopia.Instagame.databinding.FragmentDashboardBinding;
import com.genzopia.Instagame.reelview.ReelAdapter;
import com.genzopia.Instagame.reelview.ReelItem;
import com.genzopia.Instagame.reelview.ReelRepository;
import com.genzopia.Instagame.reelview.PerformanceBenchmark;
import com.genzopia.Instagame.vertical_recylerview_custom.TempStorage;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private ReelAdapter reelAdapter;
    private List<ReelItem> reelItems = new ArrayList<>();
    private RecyclerView reelView;
    private ReelRepository reelRepository;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        reelView = root.findViewById(R.id.reel_view);

        LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(),
                LinearLayoutManager.VERTICAL,
                false
        );
        if (TempStorage.videoId != null) {
            Toast.makeText(requireContext(), TempStorage.videoId, Toast.LENGTH_SHORT).show();
            TempStorage.videoId = null; // Clear after use
        }
        reelView.setLayoutManager(layoutManager);

        SnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(reelView);

        reelView.setNestedScrollingEnabled(false);

        // Initialize adapter with empty list
        reelAdapter = new ReelAdapter(requireContext(), reelItems, reelView);
        reelView.setAdapter(reelAdapter);

        // Add scroll listener for smooth video transitions
        reelView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (reelAdapter != null) {
                    reelAdapter.handleScrollStateChange(newState);
                }
            }
        });

        reelRepository = new ReelRepository();
        loadMoreReels();

        // Preload follow states for better performance
        reelAdapter.preloadFollowStates();

        // Check if there's a specific video to play
        checkForSpecificVideoToPlay();
        
        // PERFORMANCE VALIDATION: Run benchmark in debug builds
        if (BuildConfig.DEBUG) {
            // Run performance benchmark after initial load (delayed to avoid interference)
            reelView.postDelayed(() -> {
                if (isAdded() && reelAdapter != null) {
                    Log.d("DashboardFragment", "Running performance benchmark...");
                    PerformanceBenchmark.BenchmarkResults results = reelAdapter.runPerformanceBenchmark();
                    Log.i("DashboardFragment", "Performance benchmark completed: " + results.toString());
                }
            }, 5000); // Run after 5 seconds to allow initial loading to complete
        }

        return root;
    }

    private void loadMoreReels() {
        isLoadingMore = true;
        reelRepository.fetchReelsPage(new ReelRepository.ReelDataCallback() {
            @Override
            public void onReelsLoaded(List<ReelItem> reels) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    int oldSize = reelItems.size();
                    reelItems.addAll(reels);
                    if (reelAdapter != null) {
                        reelAdapter.notifyItemRangeInserted(oldSize, reels.size());
                        // Preload follow states for new reels
                        reelAdapter.preloadFollowStates();

                        // OPTIMIZED: Trigger initial preload IMMEDIATELY at position 0
                        if (oldSize == 0) {
                            Log.d("DashboardFragment", "INITIAL LOAD: Starting preload at position 0");
                            reelAdapter.updatePreloadManagerPosition(0);

                            // OPTIMIZATION: Reduced delay from 2500ms to 800ms
                            // Preload manager now works asynchronously and faster
                            reelView.postDelayed(() -> {
                                Log.d("DashboardFragment", "Playing first video");
                                if (isAdded()) {
                                    reelAdapter.ensureOnlyCurrentVideoPlays();
                                }
                            }, 800); // Reduced from 2500ms - preload is now more efficient
                        }
                    }
                    isLoadingMore = false;
                    hasMore = reelRepository.hasMore();
                });
            }
            @Override
            public void onError(String errorMessage) {
                isLoadingMore = false;
                if (getContext() != null && isAdded()) {
                    Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkForSpecificVideoToPlay() {
        try {
            com.genzopia.Instagame.utils.VideoNavigationManager manager = 
                com.genzopia.Instagame.utils.VideoNavigationManager.getInstance();
            
            if (manager.shouldPlayInReelView()) {
                String videoIdToPlay = manager.getPendingVideoId();
                
                if (videoIdToPlay != null) {
                    // Create a new reel item for this specific video and add it to the top
                    createAndAddVideoToTop(videoIdToPlay);
                }
            }
        } catch (Exception e) {
            Log.e("DashboardFragment", "Error checking for specific video: " + e.getMessage());
        }
    }
    
    private void createAndAddVideoToTop(String videoId) {
        try {
            // Create a new ReelItem for this specific video with required 6 parameters
            ReelItem newReelItem = new ReelItem(
                videoId,           // videoId
                "Loading...",      // title
                "0",               // likeCount
                "",                // description
                "",                // developerId
                ""                 // gameid
            );
            
            // Add this item to the top of the list
            reelItems.add(0, newReelItem);
            
            // Notify adapter of the change
            if (reelAdapter != null) {
                reelAdapter.notifyItemInserted(0);
                
                // Scroll to the top to show the new video
                reelView.post(() -> {
                    reelView.scrollToPosition(0);
                    
                    // Explicitly trigger video playback at position 0
                    reelAdapter.playVideoAtPosition(0);
                });
            }
            
            // Load the video data from Firebase
            loadVideoDataFromFirebase(videoId, newReelItem);
            
            Toast.makeText(requireContext(), "Loading video: " + videoId, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("DashboardFragment", "Error creating reel item: " + e.getMessage());
        }
    }
    
    private void loadVideoDataFromFirebase(String videoId, ReelItem reelItem) {
        DatabaseReference videoRef = FirebaseDatabase.getInstance().getReference("videos").child(videoId);
        videoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Update the reel item with data from Firebase
                    String gameId = snapshot.child("game_id").getValue(String.class);
                    String userId = snapshot.child("user_id").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    String videoTitle = snapshot.child("video_title").getValue(String.class);
                    String likeCount = snapshot.child("like_count").getValue(String.class);
                    
                    if (gameId != null) reelItem.setGameid(gameId);
                    if (userId != null) reelItem.setDeveloperId(userId);
                    if (description != null) reelItem.setDescription(description);
                    if (videoTitle != null) reelItem.setTitle(videoTitle);
                    if (likeCount != null) reelItem.setLikeCount(likeCount);
                    
                    // Load signed video URL from worker
                    loadSignedVideoUrl(videoId, reelItem);
                    
                    // Notify adapter to refresh the item
                    if (reelAdapter != null) {
                        reelAdapter.notifyItemChanged(0);
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("DashboardFragment", "Error loading video data: " + error.getMessage());
            }
        });
    }
    
    private void loadSignedVideoUrl(String videoId, ReelItem reelItem) {
        try {
            String videoUrl = "https://video-signer.genzopia.workers.dev/?path=video/" + videoId;
            Log.d("DashboardFragment", "Loading signed URL: " + videoUrl);
            
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder().url(videoUrl).build();
            
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                    Log.e("DashboardFragment", "Failed to get signed URL: " + e.getMessage());
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to load video", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                    try {
                        String body = response.body().string();
                        org.json.JSONObject obj = new org.json.JSONObject(body);
                        
                        if (obj.optBoolean("success")) {
                            String signedUrl = obj.optString("url");
                            reelItem.setVideoUrl(signedUrl);
                            Log.d("DashboardFragment", "Got signed URL: " + signedUrl);
                            
                            requireActivity().runOnUiThread(() -> {
                                // Notify adapter to refresh the item with the video URL
                                if (reelAdapter != null) {
                                    reelAdapter.notifyItemChanged(0);
                                    
                                    // OPTIMIZATION: Use View.postDelayed instead of creating new Handler
                                    reelView.postDelayed(() -> {
                                        reelAdapter.playVideoAtPosition(0);
                                    }, 500);
                                }
                            });
                        } else {
                            Log.e("DashboardFragment", "Worker returned error: " + obj.optString("error", "Unknown error"));
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "Failed to get video URL", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        Log.e("DashboardFragment", "Error parsing worker response: " + e.getMessage());
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Error loading video", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e("DashboardFragment", "Error setting up video URL request: " + e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (reelAdapter != null) {
            reelAdapter.pausePlayers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (reelAdapter != null) {
            // Add a small delay to ensure the view is properly attached
            reelView.postDelayed(() -> {
                reelAdapter.ensureOnlyCurrentVideoPlays();
            }, 100); // 100ms delay
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (reelAdapter != null) {
            reelAdapter.pausePlayers();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (reelAdapter != null) {
            reelAdapter.releaseAllPlayers();
        }
        binding = null;
    }
}
