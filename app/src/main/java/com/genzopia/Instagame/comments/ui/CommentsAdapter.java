package com.genzopia.Instagame.comments.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.comments.models.Comment;

/**
 * Like-only (no dislike). Reply button calls onReply so the Fragment
 * can show the "Replying to @name" banner — same as web.
 */
public class CommentsAdapter extends ListAdapter<Comment, CommentsAdapter.CommentVH> {

    public interface OnCommentActionListener {
        void onLoadReplies(@NonNull Comment c, @NonNull RecyclerView repliesList);
        void onReply(@NonNull Comment c);
        void onToggleLike(@NonNull Comment c);
        void checkLiked(@NonNull Comment c, @NonNull LikeState state);
        void onReport(@NonNull Comment c);
    }

    public interface LikeState { void setInitial(boolean liked); }

    private final OnCommentActionListener listener;

    public CommentsAdapter(OnCommentActionListener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    private static final DiffUtil.ItemCallback<Comment> DIFF = new DiffUtil.ItemCallback<Comment>() {
        @Override public boolean areItemsTheSame(@NonNull Comment a, @NonNull Comment b) {
            return a.comment_id != null && a.comment_id.equals(b.comment_id);
        }
        @Override public boolean areContentsTheSame(@NonNull Comment a, @NonNull Comment b) {
            return eq(a.text, b.text) && eq(a.like_count, b.like_count) && eq(a.reply_count, b.reply_count);
        }
        private boolean eq(Object a, Object b) { return a == b || (a != null && a.equals(b)); }
    };

    @Override public long getItemId(int position) {
        Comment c = getItem(position);
        return c != null && c.comment_id != null ? c.comment_id.hashCode() : position;
    }

    @NonNull @Override
    public CommentVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentVH(v);
    }

    @Override public void onBindViewHolder(@NonNull CommentVH h, int pos) { h.bind(getItem(pos)); }

    class CommentVH extends RecyclerView.ViewHolder {
        ImageView avatar, likeIcon, menuIcon;
        TextView name, text, meta, replyBtn, viewRepliesBtn, likeCount;
        View likeContainer, dislikeContainer;
        RecyclerView repliesList;

        CommentVH(@NonNull View v) {
            super(v);
            avatar        = v.findViewById(R.id.comment_avatar);
            name          = v.findViewById(R.id.comment_name);
            text          = v.findViewById(R.id.comment_text);
            meta          = v.findViewById(R.id.comment_meta);
            replyBtn      = v.findViewById(R.id.comment_reply_btn);
            viewRepliesBtn= v.findViewById(R.id.comment_view_replies_btn);
            likeIcon      = v.findViewById(R.id.comment_like_icon);
            likeCount     = v.findViewById(R.id.comment_like_count);
            likeContainer = v.findViewById(R.id.comment_like_container);
            menuIcon      = v.findViewById(R.id.comment_menu);
            dislikeContainer = v.findViewById(R.id.comment_dislike_container);
            repliesList   = v.findViewById(R.id.replies_list);
        }

        void bind(Comment c) {
            if (c == null) return;

            // Hide dislike — not in web version
            if (dislikeContainer != null) dislikeContainer.setVisibility(View.GONE);

            name.setText(c.user_display_name != null ? c.user_display_name : "");
            text.setText(c.text != null ? c.text : "");
            if (meta != null) meta.setText("");

            Glide.with(itemView.getContext())
                    .load(c.user_photo_url)
                    .placeholder(R.drawable.demo_user)
                    .error(R.drawable.demo_user)
                    .into(avatar);

            // Like count
            long count = c.like_count != null ? c.like_count : 0L;
            if (likeCount != null) likeCount.setText(count > 0 ? String.valueOf(count) : "");

            // Like state from ViewModel (via listener)
            if (listener != null) listener.checkLiked(c, liked -> updateLikeIcon(liked));

            // Like click — delegate to ViewModel (optimistic handled there)
            View.OnClickListener likeClick = v -> {
                if (listener != null) listener.onToggleLike(c);
            };
            if (likeContainer != null) likeContainer.setOnClickListener(likeClick);
            if (likeIcon != null) likeIcon.setOnClickListener(likeClick);

            // Reply button — tells Fragment to show banner (mirrors web onReply)
            if (replyBtn != null) replyBtn.setOnClickListener(v -> {
                if (listener != null) listener.onReply(c);
            });

            // View replies
            boolean hasReplies = c.reply_count != null && c.reply_count > 0L;
            if (viewRepliesBtn != null) {
                viewRepliesBtn.setVisibility(hasReplies ? View.VISIBLE : View.GONE);
                viewRepliesBtn.setOnClickListener(v -> {
                    if (listener != null) listener.onLoadReplies(c, repliesList);
                });
            }

            // Report
            if (menuIcon != null) menuIcon.setOnClickListener(v -> {
                if (listener != null) listener.onReport(c);
            });
        }

        private void updateLikeIcon(boolean liked) {
            if (likeIcon == null) return;
            likeIcon.setColorFilter(liked
                    ? itemView.getResources().getColor(R.color.button_primary)
                    : itemView.getResources().getColor(R.color.text_secondary));
        }
    }
}
