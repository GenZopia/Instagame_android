package com.genzopia.Instagame.comments.data;

import androidx.annotation.NonNull;

import com.genzopia.Instagame.comments.models.Comment;
import com.genzopia.Instagame.comments.models.Reply;
import com.google.firebase.database.*;

import java.util.*;

public class CommentsRepository {

    public interface CommentsCallback {
        void onLoaded(java.util.List<Comment> comments, Long lastCreatedAt, boolean hasMore);
        void onError(String message);
    }

    public interface RepliesCallback {
        void onLoaded(java.util.List<Reply> replies, Long lastCreatedAt, boolean hasMore);
        void onError(String message);
    }

    public interface CompletionCallback {
        void onComplete(boolean success, String errorMessage);
    }

    public interface BooleanCallback {
        void onResult(boolean value);
    }

    private DatabaseReference commentsRef(String videoId) {
        return FirebaseDatabase.getInstance().getReference("videos").child(videoId).child("comments");
    }

    public void fetchCommentsFirstPage(String videoId, final CommentsCallback callback) {
        Query q = commentsRef(videoId).orderByChild("created_at").limitToLast(20);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> list = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Comment c = ds.getValue(Comment.class);
                    if (c != null) list.add(c);
                }
                list.sort((a,b) -> Long.compare(b.created_at != null ? b.created_at : 0L, a.created_at != null ? a.created_at : 0L));
                Long last = list.isEmpty() ? null : list.get(list.size() - 1).created_at;
                boolean hasMore = list.size() == 20;
                callback.onLoaded(list, last, hasMore);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void fetchCommentsNextPage(String videoId, Long lastCreatedAt, final CommentsCallback callback) {
        if (lastCreatedAt == null) {
            callback.onLoaded(Collections.emptyList(), null, false);
            return;
        }
        Query q = commentsRef(videoId).orderByChild("created_at").endAt(lastCreatedAt).limitToLast(21);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> list = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Comment c = ds.getValue(Comment.class);
                    if (c != null) list.add(c);
                }
                list.sort((a,b) -> Long.compare(b.created_at != null ? b.created_at : 0L, a.created_at != null ? a.created_at : 0L));
                if (!list.isEmpty()) list.remove(0); // drop overlap
                Long last = list.isEmpty() ? null : list.get(list.size() - 1).created_at;
                boolean hasMore = list.size() >= 20;
                callback.onLoaded(list, last, hasMore);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void fetchRepliesFirstPage(String videoId, String commentId, final RepliesCallback callback) {
        DatabaseReference ref = commentsRef(videoId).child(commentId).child("replies");
        Query q = ref.orderByChild("created_at").limitToLast(20);
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Reply> list = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Reply r = ds.getValue(Reply.class);
                    if (r != null) list.add(r);
                }
                list.sort((a,b) -> Long.compare(a.created_at != null ? a.created_at : 0L, b.created_at != null ? b.created_at : 0L));
                Long last = list.isEmpty() ? null : list.get(list.size() - 1).created_at;
                boolean hasMore = list.size() == 20;
                callback.onLoaded(list, last, hasMore);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    public void postComment(String videoId, String text, String uid, String displayName, String photoUrl, final CompletionCallback callback) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference newRef = root.child("videos").child(videoId).child("comments").push();
        String commentId = newRef.getKey();

        java.util.Map<String, Object> comment = new java.util.HashMap<>();
        comment.put("comment_id", commentId);
        comment.put("user_id", uid);
        comment.put("user_display_name", displayName);
        comment.put("user_photo_url", photoUrl);
        comment.put("text", text);
        comment.put("created_at", ServerValue.TIMESTAMP);
        comment.put("like_count", 0L);
        comment.put("reply_count", 0L);

        newRef.setValue(comment, (error, ref) -> {
            if (error != null) {
                callback.onComplete(false, error.getMessage());
            } else {
                root.child("videos").child(videoId).child("comment_count")
                    .runTransaction(new Transaction.Handler() {
                        @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Long v = currentData.getValue(Long.class);
                            currentData.setValue(v == null ? 1L : v + 1L);
                            return Transaction.success(currentData);
                        }
                        @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot snapshot) {
                            callback.onComplete(e == null, e != null ? e.getMessage() : null);
                        }
                    });
            }
        });
    }

    public void postReply(String videoId, String commentId, String text, String uid, String displayName, String photoUrl, final CompletionCallback callback) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference newRef = root.child("videos").child(videoId).child("comments").child(commentId).child("replies").push();
        String replyId = newRef.getKey();

        java.util.Map<String, Object> reply = new java.util.HashMap<>();
        reply.put("reply_id", replyId);
        reply.put("parent_comment_id", commentId);
        reply.put("user_id", uid);
        reply.put("user_display_name", displayName);
        reply.put("user_photo_url", photoUrl);
        reply.put("text", text);
        reply.put("created_at", ServerValue.TIMESTAMP);
        reply.put("like_count", 0L);

        newRef.setValue(reply, (error, ref) -> {
            if (error != null) {
                callback.onComplete(false, error.getMessage());
            } else {
                root.child("videos").child(videoId).child("comments").child(commentId).child("reply_count")
                    .runTransaction(new Transaction.Handler() {
                        @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Long v = currentData.getValue(Long.class);
                            currentData.setValue(v == null ? 1L : v + 1L);
                            return Transaction.success(currentData);
                        }
                        @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot snapshot) {
                            callback.onComplete(e == null, e != null ? e.getMessage() : null);
                        }
                    });
            }
        });
    }

    public void isCommentLiked(String videoId, String commentId, String uid, final BooleanCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("videos").child(videoId)
                .child("comment_likes").child(commentId).child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { cb.onResult(snapshot.exists()); }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onResult(false); }
        });
    }

    public void setCommentLike(String videoId, String commentId, String uid, boolean like, final CompletionCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference likeRef = root.child("videos").child(videoId).child("comment_likes").child(commentId).child(uid);
        DatabaseReference countRef = root.child("videos").child(videoId).child("comments").child(commentId).child("like_count");
        likeRef.setValue(like ? Boolean.TRUE : null, (error, ref) -> {
            if (error != null) {
                cb.onComplete(false, error.getMessage());
            } else {
                countRef.runTransaction(new Transaction.Handler() {
                    @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Long v = currentData.getValue(Long.class);
                        long base = v == null ? 0L : v;
                        currentData.setValue(Math.max(0L, base + (like ? 1L : -1L)));
                        return Transaction.success(currentData);
                    }
                    @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot snapshot) {
                        if (e != null) { cb.onComplete(false, e.getMessage()); return; }
                        // Mirror state under users/{uid}/comment_likes/{commentId}
                        DatabaseReference userRef = root.child("users").child(uid).child("comment_likes").child(commentId);
                        if (like) {
                            userRef.setValue(Boolean.TRUE, (er, r) -> cb.onComplete(er == null, er != null ? er.getMessage() : null));
                        } else {
                            userRef.removeValue((er, r) -> cb.onComplete(er == null, er != null ? er.getMessage() : null));
                        }
                    }
                });
            }
        });
    }
}


