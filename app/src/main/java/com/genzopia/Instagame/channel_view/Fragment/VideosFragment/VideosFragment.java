package com.genzopia.Instagame.channel_view.Fragment.VideosFragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class VideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private static List<VideoItem_channel> sVideoList;
    private static String sDeveloperId;
    private static String sCurrentUserId;
    private static boolean sIsDataLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_videos, container, false);

        recyclerView = view.findViewById(R.id.videosRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3, RecyclerView.VERTICAL, false));

        // Initialize video list if null (preserve data across view recreation)
        if (sVideoList == null) {
            sVideoList = new ArrayList<>();
        }

        adapter = new VideoAdapter(getContext(), sVideoList);
        recyclerView.setAdapter(adapter);
        
        // Get current user ID
        sCurrentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Load videos only if not already loaded
        if (sDeveloperId != null && !sIsDataLoaded) {
            loadVideosFromFirebase();
        }

        return view;
    }
    
    public void setDeveloperId(String developerId) {
        sDeveloperId = developerId;
        if (isAdded() && recyclerView != null && !sIsDataLoaded) {
            loadVideosFromFirebase();
        }
    }
    
    private void loadVideosFromFirebase() {
        if (sDeveloperId == null) {
            Log.e("VideosFragment", "Developer ID is null");
            return;
        }
        
        Log.d("VideosFragment", "Loading videos for developer: " + sDeveloperId);
        Log.d("VideosFragment", "Current user ID: " + sCurrentUserId);
        Log.d("VideosFragment", "Is viewing own channel: " + (sCurrentUserId.equals(sDeveloperId)));
        
        // Get the developer's videos
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(sDeveloperId).child("videos");
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                sVideoList.clear();
                sIsDataLoaded = true;
                
                Log.d("VideosFragment", "Videos snapshot exists: " + snapshot.exists());
                Log.d("VideosFragment", "Videos snapshot children count: " + snapshot.getChildrenCount());
                
                if (snapshot.exists()) {
                    // Iterate through the developer's videos
                    for (DataSnapshot videoSnapshot : snapshot.getChildren()) {
                        String videoId = videoSnapshot.getKey();
                        Log.d("VideosFragment", "Found video ID: " + videoId);
                        if (videoId != null) {
                            // Fetch video details from videos collection
                            loadVideoDetails(videoId);
                        }
                    }
                } else {

                    adapter.notifyDataSetChanged();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("VideosFragment", "Error loading developer videos: " + error.getMessage());
            }
        });
    }
    
    private void loadVideoDetails(String videoId) {
        Log.d("VideosFragment", "Loading video details for video ID: " + videoId);
        
        DatabaseReference videoRef = FirebaseDatabase.getInstance().getReference("videos").child(videoId);
        videoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("VideosFragment", "Video details snapshot exists: " + snapshot.exists());
                
                if (snapshot.exists()) {
                    String title = snapshot.child("video_title").getValue(String.class);
                    String viewCount = "0 views";
                    try{
                        viewCount = String.valueOf(snapshot.child("view_count").getValue(Long.class));
                    }catch(Exception e){
                        viewCount =snapshot.child("view_count").getValue(String.class);
                    }


                    
                    // Handle is_verified field - it might be stored as String or Boolean
                    Boolean isVerified = null;
                    try {
                        Object isVerifiedObj = snapshot.child("is_verified").getValue();
                        if (isVerifiedObj instanceof Boolean) {
                            isVerified = (Boolean) isVerifiedObj;
                        } else if (isVerifiedObj instanceof String) {
                            isVerified = Boolean.parseBoolean((String) isVerifiedObj);
                        } else if (isVerifiedObj instanceof Long) {
                            isVerified = ((Long) isVerifiedObj) == 1L;
                        } else if (isVerifiedObj instanceof Integer) {
                            isVerified = ((Integer) isVerifiedObj) == 1;
                        }
                        // If isVerifiedObj is null, isVerified will remain null (which is fine)
                    } catch (Exception e) {
                        Log.e("VideosFragment", "Error parsing is_verified field: " + e.getMessage());
                        isVerified = null; // Default to null (unverified)
                    }
                    
                    String videoUserId = snapshot.child("user_id").getValue(String.class);
                    
                    Log.d("VideosFragment", "Video title: " + title);
                    Log.d("VideosFragment", "Video view count: " + viewCount);
                    Log.d("VideosFragment", "Video is verified: " + isVerified);
                    Log.d("VideosFragment", "Video user ID: " + videoUserId);
                    
                    // Check if user can view this video
                    boolean canViewVideo = shouldShowVideo(isVerified, videoUserId);
                    
                    if (canViewVideo) {
                        // Create VideoItem with all details for professional display
                        VideoItem_channel videoItem = new VideoItem_channel(
                            videoId,
                            "", // Empty thumbnail URL - will be generated from video
                            viewCount != null ? viewCount + " views" : "0 views",
                            title != null ? title : "Untitled Video",
                            isVerified,
                            sCurrentUserId.equals(sDeveloperId) // Check if viewing own channel
                        );
                        
                        sVideoList.add(videoItem);
                        adapter.notifyDataSetChanged();
                        
                        Log.d("VideosFragment", "Added video: " + title + " (Total videos in list: " + sVideoList.size() + ")");
                    } else {
                        Log.d("VideosFragment", "Skipping video: " + title + " - not verified or not owned by user");
                    }
                } else {
                    Log.e("VideosFragment", "Video details not found for video ID: " + videoId);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("VideosFragment", "Error loading video details: " + error.getMessage());
            }
        });
    }
    
    private boolean shouldShowVideo(Boolean isVerified, String videoUserId) {
        // If viewing own channel (current user = developer), show all videos
        if (sCurrentUserId.equals(sDeveloperId)) {
            Log.d("VideosFragment", "User viewing own channel - showing all videos");
            return true;
        }
        
        // If viewing someone else's channel, only show verified videos
        // Handle null isVerified as unverified (false)
        boolean isVideoVerified = (isVerified != null && isVerified);
        
        if (isVideoVerified) {
            Log.d("VideosFragment", "User viewing other's channel - showing verified video");
            return true;
        } else {
            Log.d("VideosFragment", "User viewing other's channel - hiding unverified video");
            return false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.releaseResources();
        }
    }
}