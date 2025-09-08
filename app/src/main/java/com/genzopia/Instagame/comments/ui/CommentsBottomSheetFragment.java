package com.genzopia.Instagame.comments.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.comments.data.CommentsRepository;
import com.genzopia.Instagame.comments.models.Comment;
import com.genzopia.Instagame.comments.models.Reply;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class CommentsBottomSheetFragment extends BottomSheetDialogFragment {

    public static CommentsBottomSheetFragment newInstance(String videoId) {
        Bundle b = new Bundle();
        b.putString("videoId", videoId);
        CommentsBottomSheetFragment f = new CommentsBottomSheetFragment();
        f.setArguments(b);
        return f;
    }

    private RecyclerView list;
    private CommentsAdapter adapter;
    private CommentsRepository repo;
    private String videoId;
    private Long lastCreatedAt;
    private boolean hasMore = true;
    private boolean isLoading = false;

    // Local buffering for fast like/dislike; flushed on dismiss
    private java.util.Map<String, Boolean> pendingLikes = new java.util.HashMap<>();
    private java.util.Map<String, Boolean> pendingDislikes = new java.util.HashMap<>();
    private final android.os.Handler flushHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.lang.Runnable periodicFlushRunnable = new java.lang.Runnable() {
        @Override public void run() {
            try { flushPendingReactions(); } catch (Exception ignored) {}
            // Re-schedule only while fragment is added and not destroyed
            if (isAdded() && getDialog() != null && getDialog().isShowing()) {
                flushHandler.postDelayed(this, 3000);
            }
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottomsheet_comments, container, false);
        list = v.findViewById(R.id.comments_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));

        repo = new CommentsRepository();
        adapter = new CommentsAdapter(new CommentsAdapter.OnCommentActionListener() {
            @Override public void onLoadReplies(@NonNull Comment c, @NonNull RecyclerView repliesList) {
                loadReplies(c, repliesList);
            }
            @Override public void onReply(@NonNull Comment c) {
                EditText input = v.findViewById(R.id.comment_input);
                ImageButton send = v.findViewById(R.id.comment_send_btn);
                input.setHint("Reply to " + (c.user_display_name != null ? c.user_display_name : ""));
                send.setOnClickListener(x -> postReply(c.comment_id, input));
            }
            @Override public void onToggleLike(@NonNull Comment c, boolean like, @NonNull CommentsAdapter.LikeUpdate uiUpdate) {
                // Record locally; UI already optimistically updated by adapter
                pendingLikes.put(c.comment_id, like);
                if (like) pendingDislikes.put(c.comment_id, false);
                long base = c.like_count != null ? c.like_count : 0L;
                long updated = Math.max(0L, base + (like ? 1L : -1L));
                uiUpdate.update(like, updated);
            }
            @Override public void checkLiked(@NonNull Comment c, @NonNull CommentsAdapter.LikeState state) {
                Boolean pending = pendingLikes.get(c.comment_id);
                if (pending != null) { state.setInitial(pending); return; }
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) { state.setInitial(false); return; }
                repo.isCommentLiked(videoId, c.comment_id, uid, state::setInitial);
            }
            @Override public void onToggleDislike(@NonNull Comment c, boolean dislike, @NonNull CommentsAdapter.LikeUpdate uiUpdate) {
                pendingDislikes.put(c.comment_id, dislike);
                if (dislike) pendingLikes.put(c.comment_id, false);
                long base = c.dislike_count != null ? c.dislike_count : 0L;
                long updated = Math.max(0L, base + (dislike ? 1L : -1L));
                uiUpdate.update(dislike, updated);
            }
            @Override public void checkDisliked(@NonNull Comment c, @NonNull CommentsAdapter.LikeState state) {
                Boolean pending = pendingDislikes.get(c.comment_id);
                if (pending != null) { state.setInitial(pending); return; }
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) { state.setInitial(false); return; }
                repo.isCommentDisliked(videoId, c.comment_id, uid, state::setInitial);
            }
            @Override public void onReport(@NonNull Comment c) {
                // Simple report path: mark under videos/{videoId}/comment_reports/{commentId}/{uid}=true
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) return;
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("videos").child(videoId)
                        .child("comment_reports").child(c.comment_id).child(uid)
                        .setValue(Boolean.TRUE, (error, ref) -> {
                            if (getContext() != null) {
                                android.widget.Toast.makeText(getContext(), error == null ? "Reported" : "Failed to report", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
        list.setAdapter(adapter);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!rv.canScrollVertically(1) && !isLoading && hasMore) {
                    loadNextPage();
                }
            }
        });

        videoId = getArguments() != null ? getArguments().getString("videoId") : null;
        loadFirstPage();

        ImageButton sendBtn = v.findViewById(R.id.comment_send_btn);
        EditText input = v.findViewById(R.id.comment_input);
        sendBtn.setOnClickListener(view -> postCommentOptimistic(input));

        View closeBtn = v.findViewById(R.id.comments_close);
        if (closeBtn != null) closeBtn.setOnClickListener(view -> dismiss());

        return v;
    }

    private void loadFirstPage() {
        if (videoId == null) return;
        isLoading = true;
        repo.fetchCommentsFirstPage(videoId, new CommentsRepository.CommentsCallback() {
            @Override
            public void onLoaded(@NonNull List<Comment> comments, Long last, boolean more) {
                lastCreatedAt = last;
                hasMore = more;
                isLoading = false;
                adapter.submitList(comments);
            }

            @Override
            public void onError(String message) {
                isLoading = false;
            }
        });
    }

    private void loadNextPage() {
        if (videoId == null || lastCreatedAt == null) return;
        isLoading = true;
        repo.fetchCommentsNextPage(videoId, lastCreatedAt, new CommentsRepository.CommentsCallback() {
            @Override
            public void onLoaded(@NonNull List<Comment> more, Long last, boolean hasMoreMore) {
                isLoading = false;
                lastCreatedAt = last;
                hasMore = hasMoreMore;
                List<Comment> merged = new ArrayList<>(adapter.getCurrentList());
                merged.addAll(more);
                adapter.submitList(merged);
            }

            @Override
            public void onError(String message) {
                isLoading = false;
            }
        });
    }

    private void loadReplies(Comment c, RecyclerView repliesList) {
        repliesList.setLayoutManager(new LinearLayoutManager(getContext()));
        RepliesAdapter repliesAdapter = new RepliesAdapter(new RepliesAdapter.OnReplyActionListener() {
            @Override public void onToggleLike(@NonNull Reply r, boolean like, @NonNull RepliesAdapter.LikeUpdate uiUpdate) {
                // Buffer locally similar to comments (optional). For simplicity, write-through now
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) return;
                repo.setReplyLike(videoId, c.comment_id, r.reply_id, uid, like, (success, err) -> {
                    if (!success) return;
                    long base = r.like_count != null ? r.like_count : 0L;
                    long updated = Math.max(0L, base + (like ? 1L : -1L));
                    uiUpdate.update(like, updated);
                });
            }
            @Override public void checkLiked(@NonNull Reply r, @NonNull RepliesAdapter.LikeState state) {
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) { state.setInitial(false); return; }
                repo.isReplyLiked(videoId, c.comment_id, r.reply_id, uid, state::setInitial);
            }
            @Override public void onToggleDislike(@NonNull Reply r, boolean dislike, @NonNull RepliesAdapter.LikeUpdate uiUpdate) {
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) return;
                repo.setReplyDislike(videoId, c.comment_id, r.reply_id, uid, dislike, (success, err) -> {
                    if (!success) return;
                    long base = r.dislike_count != null ? r.dislike_count : 0L;
                    long updated = Math.max(0L, base + (dislike ? 1L : -1L));
                    uiUpdate.update(dislike, updated);
                });
            }
            @Override public void checkDisliked(@NonNull Reply r, @NonNull RepliesAdapter.LikeState state) {
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) { state.setInitial(false); return; }
                repo.isReplyDisliked(videoId, c.comment_id, r.reply_id, uid, state::setInitial);
            }
        });
        repliesList.setAdapter(repliesAdapter);
        repo.fetchRepliesFirstPage(videoId, c.comment_id, new CommentsRepository.RepliesCallback() {
            @Override
            public void onLoaded(@NonNull List<Reply> replies, Long last, boolean more) {
                repliesAdapter.submitList(replies);
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void postComment(EditText input) {
        String text = input.getText() != null ? input.getText().toString().trim() : "";
        if (text.isEmpty() || videoId == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        // Resolve profile name/photo from users/{uid}
        com.google.firebase.database.DatabaseReference userRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid);
        userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String displayName = snapshot.child("full_name").getValue(String.class);
                if (displayName == null || displayName.isEmpty()) displayName = snapshot.child("username").getValue(String.class);
                String photoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                repo.postComment(videoId, text, uid, displayName, photoUrl, (success, error) -> {
                    if (success) {
                        input.setText("");
                        loadFirstPage();
                    }
                });
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        });
    }

    private void postCommentOptimistic(EditText input) {
        String text = input.getText() != null ? input.getText().toString().trim() : "";
        if (text.isEmpty() || videoId == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        // Optimistically add a local comment with temp id
        Comment temp = new Comment();
        temp.comment_id = "temp_" + System.currentTimeMillis();
        temp.user_id = uid;
        temp.user_display_name = "You";
        temp.user_photo_url = null;
        temp.text = text;
        temp.created_at = System.currentTimeMillis();
        temp.like_count = 0L;
        temp.dislike_count = 0L;
        temp.reply_count = 0L;

        java.util.List<Comment> current = new java.util.ArrayList<>(adapter.getCurrentList());
        current.add(0, temp);
        adapter.submitList(current);
        list.scrollToPosition(0);

        // Then perform real post
        com.google.firebase.database.DatabaseReference userRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid);
        userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String displayName = snapshot.child("full_name").getValue(String.class);
                if (displayName == null || displayName.isEmpty()) displayName = snapshot.child("username").getValue(String.class);
                String photoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                repo.postComment(videoId, text, uid, displayName, photoUrl, (success, error) -> {
                    if (success) {
                        input.setText("");
                        loadFirstPage();
                    } else {
                        // Remove optimistic if failed
                        java.util.List<Comment> cur = new java.util.ArrayList<>(adapter.getCurrentList());
                        cur.remove(temp);
                        adapter.submitList(cur);
                    }
                });
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                java.util.List<Comment> cur = new java.util.ArrayList<>(adapter.getCurrentList());
                cur.remove(temp);
                adapter.submitList(cur);
            }
        });
    }

    private void flushPendingReactions() {
        if (videoId == null) return;
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        // Apply likes
        for (java.util.Map.Entry<String, Boolean> e : new java.util.HashMap<>(pendingLikes).entrySet()) {
            repo.setCommentLike(videoId, e.getKey(), uid, Boolean.TRUE.equals(e.getValue()), (s, err) -> {});
        }
        // Apply dislikes
        for (java.util.Map.Entry<String, Boolean> e : new java.util.HashMap<>(pendingDislikes).entrySet()) {
            repo.setCommentDislike(videoId, e.getKey(), uid, Boolean.TRUE.equals(e.getValue()), (s, err) -> {});
        }
        pendingLikes.clear();
        pendingDislikes.clear();
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        flushPendingReactions();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start periodic flush every 3 seconds
        flushHandler.removeCallbacks(periodicFlushRunnable);
        flushHandler.postDelayed(periodicFlushRunnable, 3000);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop timer and flush immediately on pause
        flushHandler.removeCallbacks(periodicFlushRunnable);
        flushPendingReactions();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        flushHandler.removeCallbacks(periodicFlushRunnable);
    }

    private void postReply(String commentId, EditText input) {
        String text = input.getText() != null ? input.getText().toString().trim() : "";
        if (text.isEmpty() || videoId == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        com.google.firebase.database.DatabaseReference userRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid);
        userRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                String displayName = snapshot.child("full_name").getValue(String.class);
                if (displayName == null || displayName.isEmpty()) displayName = snapshot.child("username").getValue(String.class);
                String photoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                repo.postReply(videoId, commentId, text, uid, displayName, photoUrl, (success, error) -> {
                    if (success) {
                        input.setText("");
                        loadFirstPage();
                    }
                });
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        });
    }
}


