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
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) return;
                repo.setCommentLike(videoId, c.comment_id, uid, like, (success, err) -> {
                    if (!success) return;
                    long base = c.like_count != null ? c.like_count : 0L;
                    long updated = Math.max(0L, base + (like ? 1L : -1L));
                    uiUpdate.update(like, updated);
                });
            }
            @Override public void checkLiked(@NonNull Comment c, @NonNull CommentsAdapter.LikeState state) {
                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (uid == null) { state.setInitial(false); return; }
                repo.isCommentLiked(videoId, c.comment_id, uid, state::setInitial);
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
        sendBtn.setOnClickListener(view -> postComment(input));

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
        RepliesAdapter repliesAdapter = new RepliesAdapter();
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


