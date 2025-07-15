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
import com.genzopia.Instagame.databinding.FragmentDashboardBinding;
import com.genzopia.Instagame.reelview.ReelAdapter;
import com.genzopia.Instagame.reelview.ReelItem;
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

        // Load real data from Firebase
        fetchVideosFromFirebase();

        return root;
    }

    private void fetchVideosFromFirebase() {
        DatabaseReference videosRef = FirebaseDatabase.getInstance().getReference("videos");
        videosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ReelItem> loadedReels = new ArrayList<>();
                for (DataSnapshot videoSnap : snapshot.getChildren()) {
                    String videoId = videoSnap.getKey();
                    String description = videoSnap.child("description").getValue(String.class);
                    String likeCount = videoSnap.child("like_count").getValue(String.class);
                    String gameId = videoSnap.child("game_id").getValue(String.class);
                    String title = videoSnap.child("file_name").getValue(String.class);
                    String developerId = videoSnap.child("userId").getValue(String.class);
                    ReelItem item = new ReelItem(videoId, title, likeCount, description, developerId, gameId);
                    loadedReels.add(item);
                }
                fetchSignedUrlsAndGameInfo(loadedReels);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load videos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchSignedUrlsAndGameInfo(List<ReelItem> items) {
        OkHttpClient client = new OkHttpClient();
        ConcurrentHashMap<ReelItem, Boolean> loadedMap = new ConcurrentHashMap<>();
        for (ReelItem item : items) {
            loadedMap.put(item, false); // Initialize as not loaded

            // Fetch signed URL for video
            String videoUrl = "https://video-signer.genzopia.workers.dev/?path=video/" + item.getVideoId() + ".mp4";
            Request videoRequest = new Request.Builder().url(videoUrl).build();
            client.newCall(videoRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("DashboardFragment", "Signed URL fetch failed: " + e.getMessage());
                    markItemLoaded(item, items, loadedMap);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject obj = new JSONObject(body);
                        if (obj.optBoolean("success")) {
                            item.setVideoId(obj.optString("url"));
                        }
                    } catch (Exception e) {
                        Log.e("DashboardFragment", "Error parsing signed URL response", e);
                    }
                    fetchGameName(item, items, loadedMap);
                }
            });
        }
    }

    private void fetchGameName(ReelItem item, List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap) {
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(item.getGameid());
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String gameName = snapshot.child("name").getValue(String.class);
                if (gameName != null) {

                }
                markItemLoaded(item, items, loadedMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                markItemLoaded(item, items, loadedMap);
            }
        });
    }

    private void markItemLoaded(ReelItem item, List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap) {
        loadedMap.put(item, true);
        checkAllItemsLoaded(items, loadedMap);
    }

    private void checkAllItemsLoaded(List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap) {
        for (ReelItem item : items) {
            if (!loadedMap.get(item)) return; // Exit if any item isn't loaded
        }
        updateReelAdapter(items);
    }

    private void updateReelAdapter(List<ReelItem> newItems) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            reelItems.clear();
            reelItems.addAll(newItems);
            if (reelAdapter != null) {
                reelAdapter.notifyDataSetChanged();
            }
        });
    }

    // Lifecycle methods (onPause, onResume, onStop, onDestroyView) remain unchanged
    // ...


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
