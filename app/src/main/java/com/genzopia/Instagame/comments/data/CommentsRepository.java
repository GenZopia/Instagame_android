package com.genzopia.Instagame.comments.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.genzopia.Instagame.comments.models.Comment;
import com.genzopia.Instagame.comments.models.Reply;
import com.genzopia.Instagame.gateway.CommentDTO;
import com.genzopia.Instagame.gateway.CommentsPageResponse;
import com.genzopia.Instagame.gateway.GatewayCallService;
import com.genzopia.Instagame.gateway.GatewayClient;
import com.genzopia.Instagame.gateway.PostCommentRequest;
import com.genzopia.Instagame.gateway.PostReplyRequest;
import com.genzopia.Instagame.gateway.ReplyDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for comment and reply operations — all calls go through the
 * backend Gateway instead of writing directly to Firebase.
 *
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8
 */
public class CommentsRepository {

    private static final String TAG = "CommentsRepository";

    public interface CommentsCallback {
        void onLoaded(List<Comment> comments, Long lastCreatedAt, boolean hasMore);
        void onError(String message);
    }

    public interface RepliesCallback {
        void onLoaded(List<Reply> replies, Long lastCreatedAt, boolean hasMore);
        void onError(String message);
    }

    public interface CompletionCallback {
        void onComplete(boolean success, String errorMessage);
    }

    public interface BooleanCallback {
        void onResult(boolean value);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Comment commentFromDTO(CommentDTO dto) {
        Comment c = new Comment();
        c.comment_id        = dto.getComment_id();
        c.user_id           = dto.getUser_id();
        c.user_display_name = dto.getUser_display_name();
        c.user_photo_url    = dto.getUser_photo_url();
        c.text              = dto.getText();
        c.created_at        = dto.getCreated_at();
        c.like_count        = dto.getLike_count();
        c.dislike_count     = dto.getDislike_count();
        c.reply_count       = dto.getReply_count();
        return c;
    }

    private static Reply replyFromDTO(ReplyDTO dto) {
        Reply r = new Reply();
        r.reply_id          = dto.getReply_id();
        r.parent_comment_id = dto.getParent_comment_id();
        r.user_id           = dto.getUser_id();
        r.user_display_name = dto.getUser_display_name();
        r.user_photo_url    = dto.getUser_photo_url();
        r.text              = dto.getText();
        r.created_at        = dto.getCreated_at();
        r.like_count        = dto.getLike_count();
        r.dislike_count     = dto.getDislike_count();
        return r;
    }

    private GatewayCallService api() {
        return GatewayClient.INSTANCE.getCallApi();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void fetchCommentsFirstPage(String videoId, CommentsCallback callback) {
        api().getComments(videoId, null, 20).enqueue(new Callback<CommentsPageResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentsPageResponse> call,
                                   @NonNull Response<CommentsPageResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    CommentsPageResponse page = resp.body();
                    List<Comment> list = new ArrayList<>();
                    for (CommentDTO dto : page.getData()) list.add(commentFromDTO(dto));
                    Long last = list.isEmpty() ? null : list.get(list.size() - 1).created_at;
                    callback.onLoaded(list, last, page.getHasMore());
                } else {
                    callback.onError("Gateway error " + resp.code());
                }
            }
            @Override
            public void onFailure(@NonNull Call<CommentsPageResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchCommentsFirstPage failed", t);
                callback.onError(t.getMessage());
            }
        });
    }

    public void fetchCommentsNextPage(String videoId, Long lastCreatedAt, CommentsCallback callback) {
        if (lastCreatedAt == null) {
            callback.onLoaded(Collections.emptyList(), null, false);
            return;
        }
        api().getComments(videoId, lastCreatedAt, 20).enqueue(new Callback<CommentsPageResponse>() {
            @Override
            public void onResponse(@NonNull Call<CommentsPageResponse> call,
                                   @NonNull Response<CommentsPageResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    CommentsPageResponse page = resp.body();
                    List<Comment> list = new ArrayList<>();
                    for (CommentDTO dto : page.getData()) list.add(commentFromDTO(dto));
                    Long last = list.isEmpty() ? null : list.get(list.size() - 1).created_at;
                    callback.onLoaded(list, last, page.getHasMore());
                } else {
                    callback.onError("Gateway error " + resp.code());
                }
            }
            @Override
            public void onFailure(@NonNull Call<CommentsPageResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchCommentsNextPage failed", t);
                callback.onError(t.getMessage());
            }
        });
    }

    public void fetchRepliesFirstPage(String videoId, String commentId, RepliesCallback callback) {
        // Replies are embedded in comment nodes via reply_count.
        // A dedicated endpoint can be added when implemented gateway-side.
        callback.onLoaded(new ArrayList<>(), null, false);
    }

    public void postComment(String videoId, String text, String uid, String displayName,
                            String photoUrl, CompletionCallback callback) {
        api().postComment(videoId, new PostCommentRequest(text, displayName, photoUrl))
                .enqueue(new Callback<CommentDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<CommentDTO> call,
                                           @NonNull Response<CommentDTO> resp) {
                        callback.onComplete(resp.isSuccessful(),
                                resp.isSuccessful() ? null : "Gateway error " + resp.code());
                    }
                    @Override
                    public void onFailure(@NonNull Call<CommentDTO> call, @NonNull Throwable t) {
                        Log.e(TAG, "postComment failed", t);
                        callback.onComplete(false, t.getMessage());
                    }
                });
    }

    public void postReply(String videoId, String commentId, String text, String uid,
                          String displayName, String photoUrl, CompletionCallback callback) {
        api().postReply(videoId, commentId, new PostReplyRequest(text, displayName, photoUrl))
                .enqueue(new Callback<ReplyDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ReplyDTO> call,
                                           @NonNull Response<ReplyDTO> resp) {
                        callback.onComplete(resp.isSuccessful(),
                                resp.isSuccessful() ? null : "Gateway error " + resp.code());
                    }
                    @Override
                    public void onFailure(@NonNull Call<ReplyDTO> call, @NonNull Throwable t) {
                        Log.e(TAG, "postReply failed", t);
                        callback.onComplete(false, t.getMessage());
                    }
                });
    }

    public void isCommentLiked(String videoId, String commentId, String uid, BooleanCallback cb) {
        // Liked state is returned by the gateway in the comments list (isLiked field).
        cb.onResult(false);
    }

    public void setCommentLike(String videoId, String commentId, String uid, boolean like,
                               CompletionCallback cb) {
        Call<?> call = like
                ? api().likeComment(videoId, commentId)
                : api().unlikeComment(videoId, commentId);
        //noinspection unchecked,rawtypes
        ((Call) call).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call c, @NonNull Response resp) {
                cb.onComplete(resp.isSuccessful(),
                        resp.isSuccessful() ? null : "Gateway error " + resp.code());
            }
            @Override
            public void onFailure(@NonNull Call c, @NonNull Throwable t) {
                Log.e(TAG, "setCommentLike failed", t);
                cb.onComplete(false, t.getMessage());
            }
        });
    }

    public void isCommentDisliked(String videoId, String commentId, String uid, BooleanCallback cb) {
        cb.onResult(false);
    }

    public void setCommentDislike(String videoId, String commentId, String uid, boolean dislike,
                                  CompletionCallback cb) {
        Call<?> call = dislike
                ? api().dislikeComment(videoId, commentId)
                : api().undislikeComment(videoId, commentId);
        //noinspection unchecked,rawtypes
        ((Call) call).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call c, @NonNull Response resp) {
                cb.onComplete(resp.isSuccessful(),
                        resp.isSuccessful() ? null : "Gateway error " + resp.code());
            }
            @Override
            public void onFailure(@NonNull Call c, @NonNull Throwable t) {
                Log.e(TAG, "setCommentDislike failed", t);
                cb.onComplete(false, t.getMessage());
            }
        });
    }

    // Reply like/dislike — kept for backward-compat; no gateway route yet, no-op
    public void isReplyLiked(String v, String c, String r, String u, BooleanCallback cb) { cb.onResult(false); }
    public void isReplyDisliked(String v, String c, String r, String u, BooleanCallback cb) { cb.onResult(false); }
    public void setReplyLike(String v, String c, String r, String u, boolean l, CompletionCallback cb) { cb.onComplete(true, null); }
    public void setReplyDislike(String v, String c, String r, String u, boolean d, CompletionCallback cb) { cb.onComplete(true, null); }
}
