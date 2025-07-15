package com.genzopia.Instagame.reelview;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.webgl_gameloading.Game_mode;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.List;

public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.ReelViewHolder> {

    private Context context;
    private List<ReelItem> reelItems;
    private RecyclerView recyclerView;
    private SimpleExoPlayer sharedPlayer;
    private ReelViewHolder currentPlayingViewHolder = null;
    private int currentPlayingPosition = -1;
    private boolean isPausedByHold = false;

    public ReelAdapter(Context context, List<ReelItem> reelItems, RecyclerView recyclerView) {
        this.context = context;
        this.reelItems = reelItems;
        this.recyclerView = recyclerView;
        initializePlayer();
    }

    private void initializePlayer() {
        sharedPlayer = new SimpleExoPlayer.Builder(context).build();
        sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        sharedPlayer.setPlayWhenReady(false);
    }

    @NonNull
    @Override
    public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.reel_item, parent, false);
        return new ReelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
        ReelItem item = reelItems.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return reelItems.size();
    }

    @Override
    public void onViewRecycled(@NonNull ReelViewHolder holder) {
        super.onViewRecycled(holder);
        if (currentPlayingViewHolder == holder) {
            pauseCurrentVideo();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ReelViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        // Only pause if this is the currently playing view and it's completely out of view
        if (currentPlayingViewHolder == holder) {
            // Check if the view is completely out of view
            if (recyclerView.getLayoutManager() != null) {
                int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                int lastVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                
                if (holder.getAdapterPosition() < firstVisible || holder.getAdapterPosition() > lastVisible) {
                    // View is completely out of view, pause the video
                    pauseCurrentVideo();
                }
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ReelViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        // Don't auto-play here, let the scroll listener handle it
    }

    private void playVideoAtPosition(int position) {
        if (position < 0 || position >= reelItems.size()) return;
        
        // If already playing this position, do nothing
        if (currentPlayingPosition == position) return;
        
        // Pause current video and detach player
        pauseCurrentVideo();
        
        // Get the holder for this position
        ReelViewHolder holder = (ReelViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
        if (holder == null) return;
        
        // Set up the player for this video
        ReelItem item = reelItems.get(position);
        sharedPlayer.setMediaItem(MediaItem.fromUri(item.getVideoId()));
        sharedPlayer.prepare();
        sharedPlayer.setPlayWhenReady(true);
        
        // Attach player to the holder's PlayerView
        holder.playerView.setPlayer(sharedPlayer);
        
        // Update current playing state
        currentPlayingViewHolder = holder;
        currentPlayingPosition = position;
        isPausedByHold = false;
        
        // Start progress updates
        holder.startProgressUpdates();
    }

    private void pauseCurrentVideo() {
        if (sharedPlayer != null) {
            sharedPlayer.setPlayWhenReady(false);
        }
        if (currentPlayingViewHolder != null) {
            currentPlayingViewHolder.stopProgressUpdates();
            // Detach player from the current view
            currentPlayingViewHolder.playerView.setPlayer(null);
            currentPlayingViewHolder = null;
        }
        currentPlayingPosition = -1;
        isPausedByHold = false;
    }

    public void pausePlayers() {
        pauseCurrentVideo();
    }

    public void resumePlayers() {
        // Find the first visible item and play it
        if (recyclerView.getLayoutManager() != null) {
            int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            if (firstVisible >= 0 && firstVisible < getItemCount()) {
                // Only play if it's different from current position
                if (currentPlayingPosition != firstVisible) {
                    playVideoAtPosition(firstVisible);
                }
            }
        }
    }

    public void ensureOnlyCurrentVideoPlays() {
        // Pause all videos except the current one
        if (recyclerView.getLayoutManager() != null) {
            int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            if (firstVisible >= 0 && firstVisible < getItemCount()) {
                if (currentPlayingPosition != firstVisible) {
                    playVideoAtPosition(firstVisible);
                }
            }
        }
    }

    public void releaseAllPlayers() {
        pauseCurrentVideo();
        if (sharedPlayer != null) {
            sharedPlayer.release();
            sharedPlayer = null;
        }
    }

    public void handleScrollStateChange(int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            // When scrolling stops, find the first visible item and play it
            if (recyclerView.getLayoutManager() != null) {
                int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                if (firstVisible >= 0 && firstVisible < getItemCount()) {
                    // Only play if it's different from current position
                    if (currentPlayingPosition != firstVisible) {
                        playVideoAtPosition(firstVisible);
                    }
                }
            }
        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            // Don't pause video when scrolling starts, let it continue playing
            // Only pause when the view is completely out of view
        }
    }

    public class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView tvTitle, tvLikes;
        View progressLine;
        GestureDetector gestureDetector;
        String currentVideoId;
        int position;
        private android.os.Handler progressHandler;
        private Runnable progressRunnable;
        private boolean isHolding = false;

        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            progressLine = itemView.findViewById(R.id.progress_line);
            View progressContainer = itemView.findViewById(R.id.progress_container);

            playerView.setUseController(false);
            gestureDetector = new GestureDetector(context, new CustomGestureListener());

            // Add touch listener for progress container (much larger touch area)
            progressContainer.setOnTouchListener((v, event) -> {
                if (currentPlayingPosition == position && sharedPlayer != null && sharedPlayer.getDuration() > 0) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            float x = event.getX();
                            float width = progressContainer.getWidth();
                            float progress = x / width;
                            
                            // Clamp progress between 0 and 1
                            progress = Math.max(0f, Math.min(1f, progress));
                            
                            // Update progress line
                            progressLine.setScaleX(progress);
                            
                            // Seek video to the new position
                            long newPosition = (long) (progress * sharedPlayer.getDuration());
                            sharedPlayer.seekTo(newPosition);
                            
                            return true;
                        case MotionEvent.ACTION_UP:
                            return true;
                    }
                }
                return false;
            });

            itemView.setOnTouchListener((v, event) -> {
                // Let the progress container handle its own touches
                if (progressContainer.getVisibility() == View.VISIBLE) {
                    // Check if touch is on the progress container area
                    float touchY = event.getY();
                    float containerY = progressContainer.getY();
                    float containerHeight = progressContainer.getHeight();
                    
                    if (touchY >= containerY && touchY <= containerY + containerHeight) {
                        // Let the progress container handle this touch
                        return false;
                    }
                }
                
                boolean handled = gestureDetector.onTouchEvent(event);
                
                // Handle touch up for hold/pause functionality
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    onTouchUp();
                }
                
                return handled;
            });
        }

        void bind(ReelItem reelItem, int pos) {
            position = pos;
            tvTitle.setText(reelItem.getTitle());
            tvLikes.setText(reelItem.getLikeCount() + " likes");

            currentVideoId = reelItem.getVideoId();
            
            // Clear any previous player attachment
            playerView.setPlayer(null);
            
            // Reset progress line and set pivot point
            progressLine.setScaleX(0f);
            progressLine.setPivotX(0f); // Set pivot to left side for left-to-right scaling

            itemView.setTag(R.id.gameid_tag, reelItem.getGameid());
            itemView.setTag(R.id.developerid_tag, reelItem.getDeveloperId());
        }

        void startProgressUpdates() {
            stopProgressUpdates();
            progressHandler = new android.os.Handler();
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (sharedPlayer != null && sharedPlayer.getDuration() > 0) {
                        float progress = (float) sharedPlayer.getCurrentPosition() / sharedPlayer.getDuration();
                        progressLine.setScaleX(progress);
                    }
                    if (progressHandler != null) {
                        progressHandler.postDelayed(this, 100); // Update every 100ms
                    }
                }
            };
            progressHandler.post(progressRunnable);
        }

        void stopProgressUpdates() {
            if (progressHandler != null) {
                progressHandler.removeCallbacksAndMessages(null);
                progressHandler = null;
            }
            progressRunnable = null;
        }

        class CustomGestureListener extends GestureDetector.SimpleOnGestureListener {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Toggle play/pause on single tap
                if (currentPlayingPosition == position) {
                    if (sharedPlayer != null) {
                        if (sharedPlayer.isPlaying()) {
                            sharedPlayer.setPlayWhenReady(false);
                        } else {
                            sharedPlayer.setPlayWhenReady(true);
                        }
                    }
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                String gameid = (String) itemView.getTag(R.id.gameid_tag);
                String developerId = (String) itemView.getTag(R.id.developerid_tag);
                
                // Pause current video before launching activity
                pauseCurrentVideo();
                
                // Launch Game_mode activity with intent extras
                Intent intent = new Intent(context, Game_mode.class);
                intent.putExtra("developer_id", developerId);
                intent.putExtra("game_id", gameid);
                context.startActivity(intent);
                
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Handle hold to pause
                if (!isHolding && e1 != null) {
                    long pressDuration = e2.getEventTime() - e1.getDownTime();
                    if (pressDuration > 200) { // 200ms hold threshold
                        isHolding = true;
                        if (currentPlayingPosition == position && sharedPlayer != null && sharedPlayer.isPlaying()) {
                            sharedPlayer.setPlayWhenReady(false);
                            isPausedByHold = true;
                        }
                    }
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // Alternative long press detection
                isHolding = true;
                if (currentPlayingPosition == position && sharedPlayer != null && sharedPlayer.isPlaying()) {
                    sharedPlayer.setPlayWhenReady(false);
                    isPausedByHold = true;
                }
            }
        }

        // Method to handle touch up (release)
        public void onTouchUp() {
            if (isHolding && isPausedByHold && currentPlayingPosition == position) {
                if (sharedPlayer != null) {
                    sharedPlayer.setPlayWhenReady(true);
                    isPausedByHold = false;
                }
            }
            isHolding = false;
        }
    }
}

