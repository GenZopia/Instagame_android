package com.genzopia.Instagame.reelview;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

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

public class ReelRepository {
    public interface ReelDataCallback {
        void onReelsLoaded(List<ReelItem> reels);
        void onError(String errorMessage);
    }

    public void fetchReels(ReelDataCallback callback) {
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
                    String title = videoSnap.child("video_title").getValue(String.class);
                    String developerId = videoSnap.child("userId").getValue(String.class);
                    ReelItem item = new ReelItem(videoId, title, likeCount, description, developerId, gameId);
                    loadedReels.add(item);
                }
                fetchSignedUrlsAndGameInfo(loadedReels, callback);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Failed to load videos");
            }
        });
    }

    private void fetchSignedUrlsAndGameInfo(List<ReelItem> items, ReelDataCallback callback) {
        OkHttpClient client = new OkHttpClient();
        ConcurrentHashMap<ReelItem, Boolean> loadedMap = new ConcurrentHashMap<>();
        for (ReelItem item : items) {
            loadedMap.put(item, false);
            String videoUrl = "https://video-signer.genzopia.workers.dev/?path=video/" + item.getVideoId() + ".mp4";
            Request videoRequest = new Request.Builder().url(videoUrl).build();
            client.newCall(videoRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e("ReelRepository", "Signed URL fetch failed: " + e.getMessage());
                    markItemLoaded(item, items, loadedMap, callback);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject obj = new JSONObject(body);
                        if (obj.optBoolean("success")) {
                            item.setVideoUrl(obj.optString("url"));
                        }
                    } catch (Exception e) {
                        Log.e("ReelRepository", "Error parsing signed URL response", e);
                    }
                    fetchGameName(item, items, loadedMap, callback);
                }
            });
        }
    }

    private void fetchGameName(ReelItem item, List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, ReelDataCallback callback) {
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(item.getGameid());
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Optionally fetch and set game name if needed
                markItemLoaded(item, items, loadedMap, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                markItemLoaded(item, items, loadedMap, callback);
            }
        });
    }

    private void markItemLoaded(ReelItem item, List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, ReelDataCallback callback) {
        loadedMap.put(item, true);
        checkAllItemsLoaded(items, loadedMap, callback);
    }

    private void checkAllItemsLoaded(List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, ReelDataCallback callback) {
        for (ReelItem item : items) {
            if (!loadedMap.get(item)) return;
        }
        callback.onReelsLoaded(items);
    }
} 