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
import com.genzopia.Instagame.profile_recyclerview.ImageItem;
import com.genzopia.Instagame.vertical_recylerview_custom.HomeAdapter;
import com.genzopia.Instagame.vertical_recylerview_custom.VideoItem;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeAdapter adapter;
    private String currentTouchedVideoId = null;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Set up vertical RecyclerView
        RecyclerView verticalRecyclerView = binding.verticalRecyclerView;
        verticalRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Create profile items
        List<ImageItem> profileItems = new ArrayList<>();
        profileItems.add(new ImageItem("1", "https://images.unsplash.com/photo-1526779259212-939e64788e3c?fm=jpg&q=60&w=3000&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxzZWFyY2h8N3x8ZnJlZSUyMGltYWdlc3xlbnwwfHwwfHx8MA%3D%3D"));
        profileItems.add(new ImageItem("2", "https://images.unsplash.com/photo-1526779259212-939e64788e3c?fm=jpg&q=60&w=3000&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxzZWFyY2h8N3x8ZnJlZSUyMGltYWdlc3xlbnwwfHwwfHx8MA%3D%3D"));
        profileItems.add(new ImageItem("3", "https://images.unsplash.com/photo-1526779259212-939e64788e3c?fm=jpg&q=60&w=3000&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxzZWFyY2h8N3x8ZnJlZSUyMGltYWdlc3xlbnwwfHwwfHx8MA%3D%3D"));

        // Create video items
        List<VideoItem> videoItems = new ArrayList<>();
        videoItems.add(new VideoItem(
                "1",
                "Amazing Mountain Landscape",
                "Nature Channel",
                "1.2M views",
                "3 days ago",
                "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?ixlib=rb-4.0.3&auto=format&fit=crop&w=2340&q=80",
                "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?ixlib=rb-4.0.3&auto=format&fit=crop&w=880&q=80",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
        ));

        videoItems.add(new VideoItem(
                "2",
                "Sunset at the Beach",
                "Travel Adventures",
                "850K views",
                "1 week ago",
                "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?ixlib=rb-4.0.3&auto=format&fit=crop&w=2346&q=80",
                "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?ixlib=rb-4.0.3&auto=format&fit=crop&w=2070&q=80",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
        ));

        // Create and set adapter

        adapter = new HomeAdapter(requireContext(), profileItems, videoItems);
        verticalRecyclerView.setAdapter(adapter);
        adapter.setRecyclerView(verticalRecyclerView); // Add this line

// Update the global touch listener:
        adapter.setGlobalTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Prevent RecyclerView from intercepting touch events
                        if (adapter.recyclerView != null) {
                            adapter.recyclerView.requestDisallowInterceptTouchEvent(true);
                        }

                        currentTouchedVideoId = (String) v.getTag();
                        adapter.playVideo(currentTouchedVideoId);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // Continue playing as long as finger is down
                        if (currentTouchedVideoId != null) {
                            adapter.playVideo(currentTouchedVideoId);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Allow RecyclerView to intercept touch events again
                        if (adapter.recyclerView != null) {
                            adapter.recyclerView.requestDisallowInterceptTouchEvent(false);
                        }

                        if (currentTouchedVideoId != null) {
                            adapter.pauseVideo(currentTouchedVideoId);
                            currentTouchedVideoId = null;
                        }
                        return true;
                }
                return false;
            }
        });

        return root;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.releaseAllPlayers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.releaseAllPlayers();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.releaseAllPlayers();
            adapter = null;
        }
        binding = null;
    }
}