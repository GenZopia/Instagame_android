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

        // Load user's following list first, then load videos
        loadUserFollowingList();

        return root;
    }

    private void loadUserFollowingList() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference userFollowingRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUserId)
                .child("following_list");

        userFollowingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                followingList.clear();
                for (DataSnapshot followingSnap : snapshot.getChildren()) {
                    String developerId = followingSnap.getKey();
                    if (developerId != null) {
                        followingList.add(developerId);
                    }
                }
                
                // Load profile items for following list
                loadProfileItems();
                
                // Load videos from following channels
                loadVideosFromFollowing();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading following list: " + error.getMessage());
                Toast.makeText(requireContext(), "Failed to load following list", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProfileItems() {
        if (followingList.isEmpty()) {
            // Show empty state
            profileItems.clear();
            homeAdapter.notifyDataSetChanged();
            return;
        }

        // Clear existing profile items to prevent duplicates
        profileItems.clear();
        
        // Use a Set to track loaded profiles to prevent duplicates
        java.util.Set<String> loadedProfiles = new java.util.HashSet<>();
        
        Log.d("HomeFragment", "Loading profiles for " + followingList.size() + " following users");
        
        // Load profile information for each following user (only once per user)
        for (String developerId : followingList) {
            // Skip if already loaded
            if (loadedProfiles.contains(developerId)) {
                Log.d("HomeFragment", "Skipping duplicate profile for: " + developerId);
                continue;
            }
            
            loadedProfiles.add(developerId);
            Log.d("HomeFragment", "Loading profile for: " + developerId);
            
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
                            homeAdapter.notifyDataSetChanged();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("HomeFragment", "Error loading profile: " + error.getMessage());
                }
            });
        }
    }

    private void loadVideosFromFollowing() {
        if (followingList.isEmpty()) {
            // Show empty state
            videoItems.clear();
            homeAdapter.notifyDataSetChanged();
            return;
        }

        isLoadingMore = true;
        
        // Clear existing videos to prevent duplicates
        videoItems.clear();
        
        // Use a Set to track loaded videos to prevent duplicates
        java.util.Set<String> loadedVideos = new java.util.HashSet<>();
        
        // Load videos from all following channels
        for (String developerId : followingList) {
            loadVideosFromDeveloper(developerId, loadedVideos);
        }
    }

    private void loadVideosFromDeveloper(String developerId, java.util.Set<String> loadedVideos) {
        DatabaseReference userVideosRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(developerId)
                .child("videos");

        userVideosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("HomeFragment", "Loading videos for developer: " + developerId + ", found " + snapshot.getChildrenCount() + " videos");
                for (DataSnapshot videoSnap : snapshot.getChildren()) {
                    String videoId = videoSnap.getKey();
                    if (videoId != null && !loadedVideos.contains(videoId)) {
                        // Mark video as loaded to prevent duplicates
                        loadedVideos.add(videoId);
                        Log.d("HomeFragment", "Loading video: " + videoId + " from developer: " + developerId);
                        // Load video details from videos node
                        loadVideoDetails(videoId, developerId);
                    } else if (videoId != null) {
                        Log.d("HomeFragment", "Skipping duplicate video: " + videoId);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading videos from developer: " + error.getMessage());
            }
        });
    }

    private void loadVideoDetails(String videoId, String developerId) {
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId);

        videoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String title = snapshot.child("video_title").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    String likeCount = snapshot.child("like_count").getValue(String.class);
                    String viewCount = snapshot.child("view_count").getValue(String.class);
                    String createdAt = snapshot.child("created_at").getValue(String.class);
                    String gameId = snapshot.child("game_id").getValue(String.class);

                    // Load developer info
                    loadDeveloperInfo(developerId, videoId, title, description, likeCount, viewCount, createdAt, gameId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HomeFragment", "Error loading video details: " + error.getMessage());
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
                                    homeAdapter.notifyDataSetChanged();
                                    Log.d("HomeFragment", "Added video: " + videoItem.id + " from " + videoItem.channelName);
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

    @Override
    public void onPause() {
        super.onPause();
        if (homeAdapter != null) {
            homeAdapter.releaseAllPlayers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
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