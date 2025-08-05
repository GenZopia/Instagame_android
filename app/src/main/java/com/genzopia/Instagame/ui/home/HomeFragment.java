package com.genzopia.Instagame.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.databinding.FragmentHomeBinding;
import com.genzopia.Instagame.vertical_recylerview_custom.HomeAdapter;
import com.genzopia.Instagame.vertical_recylerview_custom.VideoItem;
import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.ImageItem;
import com.google.firebase.auth.FirebaseAuth;
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

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeAdapter homeAdapter;
    private List<VideoItem> videoItems = new ArrayList<>();
    private List<ImageItem> profileItems = new ArrayList<>();
    private RecyclerView homeRecyclerView;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;
    private String lastKey = null;
    private static final int PAGE_SIZE = 10;
    private List<String> followingList = new ArrayList<>();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        homeRecyclerView = root.findViewById(R.id.verticalRecyclerView);

        LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(),
                LinearLayoutManager.VERTICAL,
                false
        );
        homeRecyclerView.setLayoutManager(layoutManager);

        // Remove snap helper - keep original scrolling behavior
        homeRecyclerView.setNestedScrollingEnabled(false);

        // Initialize adapter with empty lists
        homeAdapter = new HomeAdapter(requireContext(), profileItems, videoItems);
        homeRecyclerView.setAdapter(homeAdapter);
        
        // Set the recyclerView reference in the adapter
        homeAdapter.setRecyclerView(homeRecyclerView);
        
        // Add scroll listener for auto-play
        homeRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Find the most visible video and play it
                    playMostVisibleVideo();
                }
            }
        });

        // Set loading state initially
        homeAdapter.setLoading(true);
        homeAdapter.notifyDataSetChanged();
        
        Log.d("HomeFragment", "Fragment created, loading state set to true");

        // Load user's following list first, then load videos
        loadUserFollowingList();

        return root;
    }

    private void loadUserFollowingList() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d("HomeFragment", "Loading following list for user: " + currentUserId);
        
        DatabaseReference userFollowingRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUserId)
                .child("following_list");

        userFollowingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("HomeFragment", "Firebase response - exists: " + snapshot.exists() + ", children count: " + snapshot.getChildrenCount());
                
                followingList.clear();
                for (DataSnapshot followingSnap : snapshot.getChildren()) {
                    String developerId = followingSnap.getKey();
                    Log.d("HomeFragment", "Found following: " + developerId);
                    if (developerId != null) {
                        followingList.add(developerId);
                    }
                }
                
                Log.d("HomeFragment", "Found " + followingList.size() + " following users");
                
                if (followingList.isEmpty()) {
                    // No following users, show empty state
                    Log.d("HomeFragment", "No following users found, showing empty state");
                    homeAdapter.setLoading(false);
                    homeAdapter.notifyDataSetChanged();
                    return;
                }
                
                // Load profile items for following list
                loadProfileItems();
                
                // Load videos from following channels
                loadVideosFromFollowing();
    }

    @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Failed to load following list: " + error.getMessage());
                // Show empty state on error
                homeAdapter.setLoading(false);
                homeAdapter.notifyDataSetChanged();
            }
        });
    }

    private void loadProfileItems() {
        Log.d("HomeFragment", "loadProfileItems called - followingList size: " + followingList.size());
        
        if (followingList.isEmpty()) {
            // Show empty state
            Log.d("HomeFragment", "Following list is empty, showing empty state");
            profileItems.clear();
            homeAdapter.setLoading(false);
            homeAdapter.notifyDataSetChanged();
                    return;
                }
                
        // Clear existing profile items to prevent duplicates
        profileItems.clear();
        java.util.Set<String> loadedProfiles = new java.util.HashSet<>();
        
        Log.d("HomeFragment", "Loading profile items for " + followingList.size() + " following users");
        
        for (String developerId : followingList) {
            if (loadedProfiles.contains(developerId)) {
                Log.d("HomeFragment", "Skipping duplicate profile: " + developerId);
                continue;
            }
            
            Log.d("HomeFragment", "Loading profile for developer: " + developerId);
            loadDeveloperInfo(developerId, loadedProfiles);
        }
    }

    private void loadDeveloperInfo(String developerId, java.util.Set<String> loadedProfiles) {
        loadedProfiles.add(developerId);
        Log.d("HomeFragment", "Loading developer info for: " + developerId);
        
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(developerId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
    @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String profilePhotoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                    
                    // Create unique profile item
                    ImageItem profileItem = new ImageItem(developerId, profilePhotoUrl != null ? profilePhotoUrl : "");
                    profileItems.add(profileItem);
                    
                    Log.d("HomeFragment", "Added profile for: " + developerId + " (username: " + username + ")");
                    
                    // Update adapter when all profiles are loaded
                    if (profileItems.size() == loadedProfiles.size()) {
                        Log.d("HomeFragment", "All profiles loaded: " + profileItems.size() + " profiles");
                        homeAdapter.updateData(profileItems, videoItems);
                    }
                } else {
                    Log.d("HomeFragment", "Developer not found: " + developerId);
        }
    }

    @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading profile: " + error.getMessage());
            }
        });
    }

    private void loadVideosFromFollowing() {
        Log.d("HomeFragment", "loadVideosFromFollowing called - followingList size: " + followingList.size());
        
        if (followingList.isEmpty()) {
            // Show empty state
            Log.d("HomeFragment", "Following list is empty, showing empty state for videos");
            videoItems.clear();
            homeAdapter.setLoading(false);
            homeAdapter.notifyDataSetChanged();
            return;
        }

        isLoadingMore = true;
        
        // Clear existing videos to prevent duplicates
        videoItems.clear();
        
        // Use a Set to track loaded videos to prevent duplicates
        java.util.Set<String> loadedVideos = new java.util.HashSet<>();
        
        // Track how many developers we're loading videos from
        final int totalDevelopers = followingList.size();
        final java.util.concurrent.atomic.AtomicInteger loadedDevelopers = new java.util.concurrent.atomic.AtomicInteger(0);
        
        Log.d("HomeFragment", "Starting to load videos from " + totalDevelopers + " developers");
        
        // Load videos from all following channels
        for (String developerId : followingList) {
            Log.d("HomeFragment", "Loading videos from developer: " + developerId);
            loadVideosFromDeveloper(developerId, loadedVideos, totalDevelopers, loadedDevelopers);
        }
    }

    private void loadVideosFromDeveloper(String developerId, java.util.Set<String> loadedVideos, final int totalDevelopers, final java.util.concurrent.atomic.AtomicInteger loadedDevelopers) {
        Log.d("HomeFragment", "loadVideosFromDeveloper called for: " + developerId);
        
        DatabaseReference userVideosRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(developerId)
                .child("videos");

        userVideosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("HomeFragment", "Loading videos for developer: " + developerId + ", found " + snapshot.getChildrenCount() + " videos");
                
                int videosLoaded = 0;
                for (DataSnapshot videoSnap : snapshot.getChildren()) {
                    String videoId = videoSnap.getKey();
                    if (videoId != null && !loadedVideos.contains(videoId)) {
                        // Mark video as loaded to prevent duplicates
                        loadedVideos.add(videoId);
                        Log.d("HomeFragment", "Loading video: " + videoId + " from developer: " + developerId);
                        // Load video details from videos node
                        loadVideoDetails(videoId, developerId);
                        videosLoaded++;
                    } else {
                        Log.d("HomeFragment", "Skipping video: " + videoId + " (already loaded or null)");
                    }
                }
                
                Log.d("HomeFragment", "Loaded " + videosLoaded + " videos from developer: " + developerId);
                
                // Increment loaded developers counter
                int currentLoaded = loadedDevelopers.incrementAndGet();
                Log.d("HomeFragment", "Loaded developers: " + currentLoaded + "/" + totalDevelopers);
                
                // If all developers are processed and no videos were found, hide loading
                if (currentLoaded >= totalDevelopers && videoItems.isEmpty()) {
                    Log.d("HomeFragment", "All developers processed, no videos found, hiding loading state");
                    homeAdapter.setLoading(false);
                    homeAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading videos from developer " + developerId + ": " + error.getMessage());
                
                // Increment loaded developers counter even on error
                int currentLoaded = loadedDevelopers.incrementAndGet();
                Log.d("HomeFragment", "Loaded developers (with errors): " + currentLoaded + "/" + totalDevelopers);
                
                // If all developers are processed and no videos were found, hide loading
                if (currentLoaded >= totalDevelopers && videoItems.isEmpty()) {
                    Log.d("HomeFragment", "All developers processed (with errors), no videos found, hiding loading state");
                    homeAdapter.setLoading(false);
                    homeAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void loadVideoDetails(String videoId, String developerId) {
        Log.d("HomeFragment", "loadVideoDetails called for video: " + videoId + " from developer: " + developerId);
        
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId);

        videoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("HomeFragment", "Video details response for " + videoId + " - exists: " + snapshot.exists());
                
                if (snapshot.exists()) {
                    String title = snapshot.child("video_title").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    String likeCount = snapshot.child("like_count").getValue(String.class);
                    String viewCount = snapshot.child("view_count").getValue(String.class);
                    String createdAt = snapshot.child("created_at").getValue(String.class);
                    String gameId = snapshot.child("gameid").getValue(String.class);
                    
                    Log.d("HomeFragment", "Video details loaded - title: " + title + ", description: " + description);
                    
                    // Load developer info for this video
                    loadDeveloperInfo(developerId, videoId, title, description, likeCount, viewCount, createdAt, gameId);
                } else {
                    Log.d("HomeFragment", "Video not found in database: " + videoId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading video details for " + videoId + ": " + error.getMessage());
            }
        });
    }

    private void loadDeveloperInfo(String developerId, String videoId, String title, String description, 
                                 String likeCount, String viewCount, String createdAt, String gameId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(developerId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String fullName = snapshot.child("full_name").getValue(String.class);
                    String profilePhotoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                    
                    // Use full name if available, otherwise use username, otherwise use "Unknown User"
                    String displayName = fullName != null && !fullName.isEmpty() ? fullName : 
                                      (username != null && !username.isEmpty() ? username : "Unknown User");
                    
                    // Create VideoItem with loaded data
                    VideoItem videoItem = new VideoItem(
                            videoId,
                            title != null ? title : "Untitled Video",
                            displayName, // Use the proper display name
                            viewCount != null ? viewCount + " views" : "0 views",
                            formatTimeAgo(createdAt),
                            profilePhotoUrl != null ? profilePhotoUrl : "",
                            "", // videoUrl will be loaded separately
                            description != null ? description : "",
                            developerId // Pass the developer ID
                    );
                    
                    // Load signed video URL
                    loadSignedVideoUrl(videoId, videoItem);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading developer info: " + error.getMessage());
            }
        });
    }

    private void loadSignedVideoUrl(String videoId, VideoItem videoItem) {
        try {
            String videoUrl = "https://video-signer.genzopia.workers.dev/?path=video/" + videoId;
            Log.d("HomeFragment", "Loading signed URL: " + videoUrl);
            
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(videoUrl).build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("HomeFragment", "Failed to get signed URL: " + e.getMessage());
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to load video", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject obj = new JSONObject(body);
                        
                        if (obj.optBoolean("success")) {
                            String signedUrl = obj.optString("url");
                            videoItem.videoUrl = signedUrl;
                            Log.d("HomeFragment", "Got signed URL: " + signedUrl);
                            
                            requireActivity().runOnUiThread(() -> {
                                Log.d("HomeFragment", "Processing video on UI thread: " + videoItem.id);
                                
                                // Check if video is already in the list to prevent duplicates
                                boolean videoExists = false;
                                for (VideoItem existingVideo : videoItems) {
                                    if (existingVideo.id.equals(videoItem.id)) {
                                        videoExists = true;
                                        break;
                                    }
                                }
                                
                                // Only add if not already in the list
                                if (!videoExists) {
                                    videoItems.add(videoItem);
                                    
                                    Log.d("HomeFragment", "Before adding video - Loading state: " + homeAdapter.isLoading + ", Video count: " + videoItems.size());
                                    
                                    // Hide loading state when first video is added
                                    if (videoItems.size() == 1) {
                                        Log.d("HomeFragment", "First video added, hiding loading state");
                                        homeAdapter.setLoading(false);
                                    }
                                    
                                    // Update adapter with new data
                                    homeAdapter.updateData(profileItems, videoItems);
                                    
                                    // Double-check the adapter state
                                    Log.d("HomeFragment", "Added video: " + videoItem.id + " from " + videoItem.channelName + ". Total videos: " + videoItems.size() + ", Loading state: " + homeAdapter.isLoading);
                                    
                                    // Force another refresh after a short delay to ensure UI updates
                                    new android.os.Handler().postDelayed(() -> {
                                        if (isAdded() && !isDetached()) {
                                            homeAdapter.updateData(profileItems, videoItems);
                                            Log.d("HomeFragment", "Forced refresh after delay");
                                            
                                            // Play the first video after a short delay
                                            if (videoItems.size() == 1) {
                                                new android.os.Handler().postDelayed(() -> {
                                                    if (isAdded() && !isDetached()) {
                                                        playMostVisibleVideo();
                                                    }
                                                }, 500);
                                            }
                                        }
                                    }, 100);
                                } else {
                                    Log.d("HomeFragment", "Video already exists: " + videoItem.id);
                                }
                            });
                        } else {
                            Log.e("HomeFragment", "Worker returned error: " + obj.optString("error", "Unknown error"));
                        }
                    } catch (Exception e) {
                        Log.e("HomeFragment", "Error parsing worker response: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e("HomeFragment", "Error setting up video URL request: " + e.getMessage());
        }
    }
    
    private void preloadThumbnails() {
        Log.d("HomeFragment", "Preloading thumbnails for " + videoItems.size() + " videos");
        
        // Create a temporary adapter to preload thumbnails
        HomeAdapter tempAdapter = new HomeAdapter(requireContext(), profileItems, videoItems);
        
        // Preload thumbnails for each video
        for (int i = 0; i < videoItems.size(); i++) {
            VideoItem videoItem = videoItems.get(i);
            if (videoItem.videoUrl != null && !videoItem.videoUrl.isEmpty()) {
                Log.d("HomeFragment", "Preloading thumbnail for video: " + videoItem.id);
                // The thumbnail will be generated when the VideoViewHolder is created
            }
        }
    }

    private String formatTimeAgo(String createdAt) {
        if (createdAt == null) return "Unknown time";
        
        try {
            // Simple time formatting - you can implement more sophisticated logic
            // For now, return a placeholder that looks realistic
            return "2 days ago"; // Placeholder - you can implement proper date parsing
        } catch (Exception e) {
            return "Unknown time";
        }
    }

    private void playMostVisibleVideo() {
        if (homeAdapter == null || videoItems.isEmpty()) {
            Log.d("HomeFragment", "Cannot play video - adapter is null or no videos");
            return;
        }
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) homeRecyclerView.getLayoutManager();
        if (layoutManager == null) {
            Log.d("HomeFragment", "LayoutManager is null");
            return;
        }
        
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        
        Log.d("HomeFragment", "Visible range: " + firstVisible + " to " + lastVisible);
        
        // Find the most visible video item
        int mostVisiblePosition = -1;
        float maxVisibility = 0f;
        
        for (int i = firstVisible; i <= lastVisible; i++) {
            if (i < 0 || i >= homeAdapter.getItemCount()) continue;
            
            // Skip profile item (position 0)
            if (i == 0) continue;
            
            View view = layoutManager.findViewByPosition(i);
            if (view != null) {
                // Calculate visibility percentage
                int[] location = new int[2];
                view.getLocationInWindow(location);
                int viewTop = location[1];
                int viewBottom = viewTop + view.getHeight();
                
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int visibleHeight = Math.min(viewBottom, screenHeight) - Math.max(viewTop, 0);
                float visibility = (float) visibleHeight / view.getHeight();
                
                Log.d("HomeFragment", "Position " + i + " visibility: " + visibility);
                
                if (visibility > maxVisibility) {
                    maxVisibility = visibility;
                    mostVisiblePosition = i;
                }
            }
        }
        
        // If no video is visible, play the first video (position 1)
        if (mostVisiblePosition == -1 && homeAdapter.getItemCount() > 1) {
            mostVisiblePosition = 1;
            Log.d("HomeFragment", "No visible video found, playing first video at position 1");
        }
        
        // Play the most visible video if it's different from current
        if (mostVisiblePosition != -1 && mostVisiblePosition != homeAdapter.currentPlayingPosition) {
            Log.d("HomeFragment", "Playing most visible video at position: " + mostVisiblePosition);
            homeAdapter.playVideoAtPosition(mostVisiblePosition);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("HomeFragment", "onResume called - Video count: " + videoItems.size() + ", Loading state: " + homeAdapter.isLoading);
        
        // Force refresh when resuming
        if (homeAdapter != null) {
            homeAdapter.updateData(profileItems, videoItems);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("HomeFragment", "onPause called");
        if (homeAdapter != null) {
            homeAdapter.releaseAllPlayers();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (homeAdapter != null) {
            homeAdapter.releaseAllPlayers();
        }
        binding = null;
    }
}