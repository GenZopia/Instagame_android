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
    private List<VideoItem_channel> videoList;
    private String developerId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_videos, container, false);

        recyclerView = view.findViewById(R.id.videosRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3, RecyclerView.VERTICAL, false));

        // Initialize video list
        videoList = new ArrayList<>();
        
        adapter = new VideoAdapter(getContext(), videoList);
        recyclerView.setAdapter(adapter);
        
        // Load videos if developer ID is set
        if (developerId != null) {
            loadVideosFromFirebase();
        }

        return view;
    }
    
    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
        if (isAdded() && recyclerView != null) {
            loadVideosFromFirebase();
        }
    }
    
    private void loadVideosFromFirebase() {
        if (developerId == null) {
            Log.e("VideosFragment", "Developer ID is null");
            return;
        }
        
        Log.d("VideosFragment", "Loading videos for developer: " + developerId);
        
        // Get the developer's videos
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(developerId).child("videos");
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                videoList.clear();
                
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
                    Log.d("VideosFragment", "No videos found for developer: " + developerId);
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
                    String viewCount = snapshot.child("view_count").getValue(String.class);
                    String thumbnailUrl = snapshot.child("thumbnail_url").getValue(String.class);
                    
                    Log.d("VideosFragment", "Video title: " + title);
                    Log.d("VideosFragment", "Video view count: " + viewCount);
                    Log.d("VideosFragment", "Video thumbnail URL: " + thumbnailUrl);
                    
                    // Create VideoItem with fetched data
                    VideoItem_channel videoItem = new VideoItem_channel(
                        videoId,
                        thumbnailUrl != null ? thumbnailUrl : "",
                        viewCount != null ? viewCount + " views" : "0 views"
                    );
                    
                    videoList.add(videoItem);
                    adapter.notifyDataSetChanged();
                    
                    Log.d("VideosFragment", "Added video: " + title + " (Total videos in list: " + videoList.size() + ")");
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
}