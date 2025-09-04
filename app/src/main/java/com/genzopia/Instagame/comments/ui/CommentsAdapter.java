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

public class CommentsAdapter extends ListAdapter<Comment, CommentsAdapter.CommentVH> {

    public interface OnCommentActionListener {
        void onLoadReplies(@NonNull Comment c, @NonNull RecyclerView repliesList);
        void onReply(@NonNull Comment c);
        void onToggleLike(@NonNull Comment c, boolean like, @NonNull LikeUpdate uiUpdate);
        void checkLiked(@NonNull Comment c, @NonNull LikeState state);
    }

    public interface LikeUpdate { void update(boolean liked, long newCount); }
    public interface LikeState { void setInitial(boolean liked); }

    private final OnCommentActionListener listener;

    public CommentsAdapter(OnCommentActionListener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    private static final DiffUtil.ItemCallback<Comment> DIFF = new DiffUtil.ItemCallback<Comment>() {
        @Override public boolean areItemsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
            return safeEq(oldItem.comment_id, newItem.comment_id);
        }
        @Override public boolean areContentsTheSame(@NonNull Comment oldItem, @NonNull Comment newItem) {
            return safeEq(oldItem.text, newItem.text)
                    && safeEq(oldItem.like_count, newItem.like_count)
                    && safeEq(oldItem.reply_count, newItem.reply_count)
                    && safeEq(oldItem.user_display_name, newItem.user_display_name)
                    && safeEq(oldItem.user_photo_url, newItem.user_photo_url);
        }
        private boolean safeEq(Object a, Object b) { return a == b || (a != null && a.equals(b)); }
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

    @Override
    public void onBindViewHolder(@NonNull CommentVH holder, int position) {
        holder.bind(getItem(position));
    }

    class CommentVH extends RecyclerView.ViewHolder {
        ImageView avatar; TextView name; TextView text; TextView meta; TextView replyBtn; TextView viewRepliesBtn; ImageView likeIcon; TextView likeCount; View likeContainer;
        RecyclerView repliesList;
        boolean isLiked = false;
        CommentVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.comment_avatar);
            name = itemView.findViewById(R.id.comment_name);
            text = itemView.findViewById(R.id.comment_text);
            meta = itemView.findViewById(R.id.comment_meta);
            replyBtn = itemView.findViewById(R.id.comment_reply_btn);
            viewRepliesBtn = itemView.findViewById(R.id.comment_view_replies_btn);
            likeIcon = itemView.findViewById(R.id.comment_like_icon);
            likeCount = itemView.findViewById(R.id.comment_like_count);
            likeContainer = itemView.findViewById(R.id.comment_like_container);
            repliesList = itemView.findViewById(R.id.replies_list);
        }
        void bind(Comment c) {
            if (c == null) return;
            name.setText(c.user_display_name != null ? c.user_display_name : "");
            text.setText(c.text != null ? c.text : "");
            meta.setText("");
            Glide.with(itemView.getContext()).load(c.user_photo_url).placeholder(R.drawable.demo_user).error(R.drawable.demo_user).into(avatar);
            replyBtn.setOnClickListener(v -> { if (listener != null) listener.onReply(c); });
            viewRepliesBtn.setVisibility(c.reply_count != null && c.reply_count.longValue() > 0L ? View.VISIBLE : View.GONE);
            viewRepliesBtn.setOnClickListener(v -> { if (listener != null) listener.onLoadReplies(c, repliesList); });

            long count = c.like_count != null ? c.like_count : 0L;
            if (likeCount != null) likeCount.setText(String.valueOf(count));

            // Initial liked state
            if (listener != null) listener.checkLiked(c, liked -> {
                isLiked = liked;
                if (likeIcon != null) likeIcon.setColorFilter(isLiked ? 0xFFFF0000 : 0xFF888888);
            });

            View.OnClickListener likeClick = v -> {
                if (listener == null) return;
                final long current = c.like_count != null ? c.like_count : 0L;
                final boolean target = !isLiked;
                listener.onToggleLike(c, target, (liked, newCount) -> {
                    if (likeCount != null) likeCount.setText(String.valueOf(newCount));
                    if (likeIcon != null) likeIcon.setColorFilter(liked ? 0xFFFF0000 : 0xFF888888);
                    c.like_count = newCount;
                    isLiked = liked;
                });
            };
            if (likeContainer != null) likeContainer.setOnClickListener(likeClick);
            if (likeIcon != null) likeIcon.setOnClickListener(likeClick);
        }
    }
}


