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
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final int PAGE_SIZE = 10;
    private static final int MAX_CONCURRENT_REQUESTS = 3;
    private String lastKey = null;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private Queue<Runnable> requestQueue = new LinkedList<>();
    private AtomicInteger runningRequests = new AtomicInteger(0);

    public void fetchReelsPage(ReelDataCallback callback) {
        if (isLoading || !hasMore) return;
        isLoading = true;
        DatabaseReference videosRef = FirebaseDatabase.getInstance().getReference("videos");
        com.google.firebase.database.Query query;
        if (lastKey == null) {
            query = videosRef.orderByKey().limitToFirst(PAGE_SIZE);
        } else {
            query = videosRef.orderByKey().startAfter(lastKey).limitToFirst(PAGE_SIZE);
        }
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ReelItem> loadedReels = new ArrayList<>();
                String newLastKey = null;
                for (DataSnapshot videoSnap : snapshot.getChildren()) {
                    String videoId = videoSnap.getKey();
                    String description = videoSnap.child("description").getValue(String.class);
                    String likeCount = videoSnap.child("like_count").getValue(String.class);
                    String gameId = videoSnap.child("game_id").getValue(String.class);
                    String title = videoSnap.child("video_title").getValue(String.class);
                    String developerId = videoSnap.child("user_id").getValue(String.class);
                    
                    // Handle null values with defaults
                    if (videoId == null) {
                        Log.w("ReelRepository", "Skipping video with null videoId");
                        continue; // Skip items without video ID
                    }
                    if (title == null) title = "Untitled Video";
                    if (likeCount == null) likeCount = "0";
                    if (description == null) description = "";
                    if (developerId == null) developerId = "";
                    if (gameId == null) {
                        Log.w("ReelRepository", "Video " + videoId + " has null gameId, using empty string");
                        gameId = ""; // Use empty string instead of null
                    }
                    
                    ReelItem item = new ReelItem(videoId, title, likeCount, description, developerId, gameId);
                    loadedReels.add(item);
                    newLastKey = videoId;
                }
                if (loadedReels.size() < PAGE_SIZE) {
                    hasMore = false;
                }
                lastKey = newLastKey;
                fetchSignedUrlsAndGameInfoPaged(loadedReels, callback);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoading = false;
                callback.onError("Failed to load videos");
            }
        });
    }

    private void fetchSignedUrlsAndGameInfoPaged(List<ReelItem> items, ReelDataCallback callback) {
        if (items.isEmpty()) {
            isLoading = false;
            callback.onReelsLoaded(items);
            return;
        }
        OkHttpClient client = new OkHttpClient();
        ConcurrentHashMap<ReelItem, Boolean> loadedMap = new ConcurrentHashMap<>();
        // Fetch all video URLs in parallel, no special handling for the first video
        fetchRestSignedUrls(items, loadedMap, client, callback, 0);
    }

    // Helper to queue the rest of the videos (from startIdx)
    private void fetchRestSignedUrls(List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, OkHttpClient client, ReelDataCallback callback, int startIdx) {
        for (int i = startIdx; i < items.size(); i++) {
            ReelItem item = items.get(i);
            loadedMap.put(item, false);
            Runnable requestTask = () -> {
                String videoUrl = "https://video-signer.genzopia.workers.dev/?path=video/" + item.getVideoId() ;
                Log.e("test5556",videoUrl);
                Request videoRequest = new Request.Builder().url(videoUrl).build();
                client.newCall(videoRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e("ReelRepository", "Signed URL fetch failed: " + e.getMessage());
                        markItemLoadedPaged(item, items, loadedMap, callback);
                        onRequestFinished();
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
                        fetchGameNamePaged(item, items, loadedMap, callback);
                        onRequestFinished();
                    }
                });
            };
            enqueueRequest(requestTask);
        }
        // Start initial batch
        for (int i = 0; i < MAX_CONCURRENT_REQUESTS; i++) {
            dequeueRequest();
        }
    }

    private void fetchGameNamePaged(ReelItem item, List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, ReelDataCallback callback) {
        String gameId = item.getGameid();
        
        // Skip game name fetch if gameId is null or empty
        if (gameId == null || gameId.isEmpty()) {
            Log.d("ReelRepository", "Skipping game name fetch for video " + item.getVideoId() + " - no gameId");
            markItemLoadedPaged(item, items, loadedMap, callback);
            return;
        }
        
        try {
            DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Optionally fetch and set game name if needed
                markItemLoadedPaged(item, items, loadedMap, callback);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                    Log.w("ReelRepository", "Failed to fetch game data for gameId: " + gameId + ", error: " + error.getMessage());
                markItemLoadedPaged(item, items, loadedMap, callback);
            }
        });
        } catch (Exception e) {
            Log.e("ReelRepository", "Error accessing Firebase for gameId: " + gameId, e);
            markItemLoadedPaged(item, items, loadedMap, callback);
        }
    }

    private void markItemLoadedPaged(ReelItem item, List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, ReelDataCallback callback) {
        loadedMap.put(item, true);
        checkAllItemsLoadedPaged(items, loadedMap, callback);
    }

    private void checkAllItemsLoadedPaged(List<ReelItem> items, ConcurrentHashMap<ReelItem, Boolean> loadedMap, ReelDataCallback callback) {
        for (ReelItem item : items) {
            if (!loadedMap.get(item)) return;
        }
        isLoading = false;
        callback.onReelsLoaded(items);
    }

    private void enqueueRequest(Runnable requestTask) {
        requestQueue.add(requestTask);
    }
    private void dequeueRequest() {
        if (runningRequests.get() >= MAX_CONCURRENT_REQUESTS) return;
        Runnable task = requestQueue.poll();
        if (task != null) {
            runningRequests.incrementAndGet();
            task.run();
        }
    }
    private void onRequestFinished() {
        runningRequests.decrementAndGet();
        dequeueRequest();
    }

    public boolean hasMore() {
        return hasMore;
    }
    public void resetPagination() {
        lastKey = null;
        hasMore = true;
        isLoading = false;
        requestQueue.clear();
        runningRequests.set(0);
    }
} 