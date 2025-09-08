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
        DatabaseReference likeCountRef = root.child("videos").child(videoId).child("comments").child(commentId).child("like_count");

        likeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot likedSnap) {
                boolean currentlyLiked = likedSnap.exists();
                if (currentlyLiked == like) { cb.onComplete(true, null); return; }

                // Apply like/unlike
        likeRef.setValue(like ? Boolean.TRUE : null, (error, ref) -> {
                    if (error != null) { cb.onComplete(false, error.getMessage()); return; }

                    likeCountRef.runTransaction(new Transaction.Handler() {
                    @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Long v = currentData.getValue(Long.class);
                        long base = v == null ? 0L : v;
                        currentData.setValue(Math.max(0L, base + (like ? 1L : -1L)));
                        return Transaction.success(currentData);
                    }
                    @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot snapshot) {
                        if (e != null) { cb.onComplete(false, e.getMessage()); return; }

                            // Mirror under users
                            DatabaseReference userLikeRef = root.child("users").child(uid).child("comment_likes").child(commentId);
                            if (like) userLikeRef.setValue(Boolean.TRUE); else userLikeRef.removeValue();

                        if (like) {
                                // If previously disliked, remove it and decrement dislike count
                                DatabaseReference dislikeRef = root.child("videos").child(videoId).child("comment_dislikes").child(commentId).child(uid);
                                DatabaseReference dislikeCountRef = root.child("videos").child(videoId).child("comments").child(commentId).child("dislike_count");
                                dislikeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                                        if (ds.exists()) {
                                            dislikeRef.removeValue();
                                            dislikeCountRef.runTransaction(new Transaction.Handler() {
                                                @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData d) {
                                                    Long val = d.getValue(Long.class);
                                                    long base = val == null ? 0L : val;
                                                    d.setValue(Math.max(0L, base - 1L));
                                                    return Transaction.success(d);
                                                }
                                                @Override public void onComplete(DatabaseError de, boolean c, DataSnapshot s) {}
                                            });
                                            root.child("users").child(uid).child("comment_dislikes").child(commentId).removeValue();
                                        }
                                        cb.onComplete(true, null);
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error1) { cb.onComplete(true, null); }
                                });
                            } else {
                                cb.onComplete(true, null);
                            }
                        }
                    });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onComplete(false, error.getMessage()); }
        });
    }

    // --- Dislike support ---
    public void isCommentDisliked(String videoId, String commentId, String uid, final BooleanCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("videos").child(videoId)
                .child("comment_dislikes").child(commentId).child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { cb.onResult(snapshot.exists()); }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onResult(false); }
        });
    }

    public void setCommentDislike(String videoId, String commentId, String uid, boolean dislike, final CompletionCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference dislikeRef = root.child("videos").child(videoId).child("comment_dislikes").child(commentId).child(uid);
        DatabaseReference countRef = root.child("videos").child(videoId).child("comments").child(commentId).child("dislike_count");
        dislikeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot dislikedSnap) {
                boolean currentlyDisliked = dislikedSnap.exists();
                if (currentlyDisliked == dislike) { cb.onComplete(true, null); return; }

                dislikeRef.setValue(dislike ? Boolean.TRUE : null, (error, ref) -> {
                    if (error != null) { cb.onComplete(false, error.getMessage()); return; }

                    countRef.runTransaction(new Transaction.Handler() {
                        @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Long v = currentData.getValue(Long.class);
                            long base = v == null ? 0L : v;
                            currentData.setValue(Math.max(0L, base + (dislike ? 1L : -1L)));
                            return Transaction.success(currentData);
                        }
                        @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot snapshot) {
                            if (e != null) { cb.onComplete(false, e.getMessage()); return; }

                            DatabaseReference userDislikeRef = root.child("users").child(uid).child("comment_dislikes").child(commentId);
                            if (dislike) userDislikeRef.setValue(Boolean.TRUE); else userDislikeRef.removeValue();

                            if (dislike) {
                                // If previously liked, remove it and decrement like count
                                DatabaseReference likeRef2 = root.child("videos").child(videoId).child("comment_likes").child(commentId).child(uid);
                                DatabaseReference likeCountRef2 = root.child("videos").child(videoId).child("comments").child(commentId).child("like_count");
                                likeRef2.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot ls) {
                                        if (ls.exists()) {
                                            likeRef2.removeValue();
                                            likeCountRef2.runTransaction(new Transaction.Handler() {
                                                @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData d) {
                                                    Long val = d.getValue(Long.class);
                                                    long base = val == null ? 0L : val;
                                                    d.setValue(Math.max(0L, base - 1L));
                                                    return Transaction.success(d);
                                                }
                                                @Override public void onComplete(DatabaseError de, boolean c, DataSnapshot s) {}
                                            });
                                            root.child("users").child(uid).child("comment_likes").child(commentId).removeValue();
                                        }
                                        cb.onComplete(true, null);
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error1) { cb.onComplete(true, null); }
                                });
                        } else {
                                cb.onComplete(true, null);
                        }
                    }
                });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onComplete(false, error.getMessage()); }
        });
    }

    // --- Reply like/dislike ---
    private DatabaseReference replyRef(String videoId, String commentId) {
        return FirebaseDatabase.getInstance().getReference("videos").child(videoId).child("comments").child(commentId).child("replies");
    }

    public void isReplyLiked(String videoId, String commentId, String replyId, String uid, final BooleanCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("videos").child(videoId)
                .child("reply_likes").child(replyId).child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { cb.onResult(snapshot.exists()); }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onResult(false); }
        });
    }

    public void isReplyDisliked(String videoId, String commentId, String replyId, String uid, final BooleanCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("videos").child(videoId)
                .child("reply_dislikes").child(replyId).child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) { cb.onResult(snapshot.exists()); }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onResult(false); }
        });
    }

    public void setReplyLike(String videoId, String commentId, String replyId, String uid, boolean like, final CompletionCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference likeRef = root.child("videos").child(videoId).child("reply_likes").child(replyId).child(uid);
        DatabaseReference likeCountRef = replyRef(videoId, commentId).child(replyId).child("like_count");

        likeRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                boolean cur = snap.exists();
                if (cur == like) { cb.onComplete(true, null); return; }

                likeRef.setValue(like ? Boolean.TRUE : null, (error, ref) -> {
                    if (error != null) { cb.onComplete(false, error.getMessage()); return; }
                    likeCountRef.runTransaction(new Transaction.Handler() {
                        @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData d) {
                            Long v = d.getValue(Long.class);
                            long base = v == null ? 0L : v;
                            d.setValue(Math.max(0L, base + (like ? 1L : -1L)));
                            return Transaction.success(d);
                        }
                        @Override public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {
                            if (e != null) { cb.onComplete(false, e.getMessage()); return; }
                            // Remove dislike if switching
                            if (like) {
                                DatabaseReference dRef = root.child("videos").child(videoId).child("reply_dislikes").child(replyId).child(uid);
                                DatabaseReference dCount = replyRef(videoId, commentId).child(replyId).child("dislike_count");
                                dRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                                        if (ds.exists()) {
                                            dRef.removeValue();
                                            dCount.runTransaction(new Transaction.Handler() {
                                                @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData md) {
                                                    Long vv = md.getValue(Long.class);
                                                    long b = vv == null ? 0L : vv;
                                                    md.setValue(Math.max(0L, b - 1L));
                                                    return Transaction.success(md);
                                                }
                                                @Override public void onComplete(DatabaseError de, boolean cc, DataSnapshot ss) {}
                                            });
                                            // remove mirror
                                            root.child("users").child(uid).child("reply_dislikes").child(replyId).removeValue();
                                        }
                                        cb.onComplete(true, null);
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error) { cb.onComplete(true, null); }
                                });
                            } else { cb.onComplete(true, null); }
                        }
                    });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onComplete(false, error.getMessage()); }
        });
    }

    public void setReplyDislike(String videoId, String commentId, String replyId, String uid, boolean dislike, final CompletionCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference dRef = root.child("videos").child(videoId).child("reply_dislikes").child(replyId).child(uid);
        DatabaseReference dCountRef = replyRef(videoId, commentId).child(replyId).child("dislike_count");

        dRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                boolean cur = snap.exists();
                if (cur == dislike) { cb.onComplete(true, null); return; }
                dRef.setValue(dislike ? Boolean.TRUE : null, (error, ref) -> {
                    if (error != null) { cb.onComplete(false, error.getMessage()); return; }
                    dCountRef.runTransaction(new Transaction.Handler() {
                        @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData d) {
                            Long v = d.getValue(Long.class);
                            long base = v == null ? 0L : v;
                            d.setValue(Math.max(0L, base + (dislike ? 1L : -1L)));
                            return Transaction.success(d);
                        }
                        @Override public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {
                            if (e != null) { cb.onComplete(false, e.getMessage()); return; }
                            // Remove like if switching
                            if (dislike) {
                                DatabaseReference lRef = root.child("videos").child(videoId).child("reply_likes").child(replyId).child(uid);
                                DatabaseReference lCount = replyRef(videoId, commentId).child(replyId).child("like_count");
                                lRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                                        if (ds.exists()) {
                                            lRef.removeValue();
                                            lCount.runTransaction(new Transaction.Handler() {
                                                @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData md) {
                                                    Long vv = md.getValue(Long.class);
                                                    long b = vv == null ? 0L : vv;
                                                    md.setValue(Math.max(0L, b - 1L));
                                                    return Transaction.success(md);
                                                }
                                                @Override public void onComplete(DatabaseError de, boolean cc, DataSnapshot ss) {}
                                            });
                                            // remove mirror
                                            root.child("users").child(uid).child("reply_likes").child(replyId).removeValue();
                                        }
                                        cb.onComplete(true, null);
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error) { cb.onComplete(true, null); }
                                });
                            } else { cb.onComplete(true, null); }
                        }
                    });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onComplete(false, error.getMessage()); }
        });
    }
}


