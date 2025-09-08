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
        void onToggleDislike(@NonNull Comment c, boolean dislike, @NonNull LikeUpdate uiUpdate);
        void checkDisliked(@NonNull Comment c, @NonNull LikeState state);
        void onReport(@NonNull Comment c);
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
        ImageView avatar; TextView name; TextView text; TextView meta; TextView replyBtn; TextView viewRepliesBtn; ImageView likeIcon; TextView likeCount; View likeContainer; ImageView menuIcon; View dislikeContainer; ImageView dislikeIcon; TextView dislikeCount;
        RecyclerView repliesList;
        boolean isLiked = false;
        boolean isDisliked = false;
        boolean likeInFlight = false;
        boolean dislikeInFlight = false;
        long lastActionAtMs = 0L;
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
            menuIcon = itemView.findViewById(R.id.comment_menu);
            dislikeContainer = itemView.findViewById(R.id.comment_dislike_container);
            dislikeIcon = itemView.findViewById(R.id.comment_dislike_icon);
            dislikeCount = itemView.findViewById(R.id.comment_dislike_count);
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
                if (likeIcon != null) likeIcon.setColorFilter(isLiked ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
            });

            View.OnClickListener likeClick = v -> {
                if (listener == null) return;
                long now = System.currentTimeMillis();
                if (likeInFlight || dislikeInFlight || now - lastActionAtMs < 250) return; // debounce
                lastActionAtMs = now;
                final boolean target = !isLiked;
                if (likeContainer != null) likeContainer.setEnabled(false);
                if (dislikeContainer != null) dislikeContainer.setEnabled(false);
                likeInFlight = true;
                // Optimistic UI
                isLiked = target;
                if (likeIcon != null) likeIcon.setColorFilter(target ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
                long base = c.like_count != null ? c.like_count : 0L;
                long next = Math.max(0L, base + (target ? 1L : -1L));
                c.like_count = next;
                if (likeCount != null) likeCount.setText(String.valueOf(next));
                if (target && isDisliked) {
                    isDisliked = false;
                    long dbase = c.dislike_count != null ? c.dislike_count : 0L;
                    long dnext = Math.max(0L, dbase - 1L);
                    c.dislike_count = dnext;
                    if (dislikeCount != null) dislikeCount.setText(String.valueOf(dnext));
                    if (dislikeIcon != null) dislikeIcon.setColorFilter(itemView.getResources().getColor(R.color.text_secondary));
                }

                listener.onToggleLike(c, target, (liked, newCount) -> {
                    // Reconcile only if values differ from optimistic
                    if (c.like_count == null || c.like_count.longValue() != newCount) {
                        c.like_count = newCount;
                        if (likeCount != null) likeCount.setText(String.valueOf(newCount));
                    }
                    if (isLiked != liked) {
                        isLiked = liked;
                        if (likeIcon != null) likeIcon.setColorFilter(liked ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
                    }
                    if (liked && isDisliked) {
                        if (dislikeIcon != null) dislikeIcon.setColorFilter(itemView.getResources().getColor(R.color.text_secondary));
                        isDisliked = false;
                        if (c.dislike_count != null && c.dislike_count > 0) {
                            c.dislike_count = c.dislike_count - 1;
                            if (dislikeCount != null) dislikeCount.setText(String.valueOf(c.dislike_count));
                        }
                    }
                    likeInFlight = false;
                    if (likeContainer != null) likeContainer.setEnabled(true);
                    if (dislikeContainer != null) dislikeContainer.setEnabled(true);
                });
            };
            if (likeContainer != null) likeContainer.setOnClickListener(likeClick);
            if (likeIcon != null) likeIcon.setOnClickListener(likeClick);

            // Dislike
            long dcount = c.dislike_count != null ? c.dislike_count : 0L;
            if (dislikeCount != null) dislikeCount.setText(String.valueOf(dcount));
            if (listener != null) listener.checkDisliked(c, disliked -> {
                isDisliked = disliked;
                if (dislikeIcon != null) dislikeIcon.setColorFilter(isDisliked ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
            });
            View.OnClickListener dislikeClick = v -> {
                if (listener == null) return;
                long now = System.currentTimeMillis();
                if (likeInFlight || dislikeInFlight || now - lastActionAtMs < 250) return; // debounce
                lastActionAtMs = now;
                final boolean target = !isDisliked;
                if (likeContainer != null) likeContainer.setEnabled(false);
                if (dislikeContainer != null) dislikeContainer.setEnabled(false);
                dislikeInFlight = true;
                // Optimistic UI
                isDisliked = target;
                if (dislikeIcon != null) dislikeIcon.setColorFilter(target ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
                long dbase = c.dislike_count != null ? c.dislike_count : 0L;
                long dnext = Math.max(0L, dbase + (target ? 1L : -1L));
                c.dislike_count = dnext;
                if (dislikeCount != null) dislikeCount.setText(String.valueOf(dnext));
                if (target && isLiked) {
                    isLiked = false;
                    long base2 = c.like_count != null ? c.like_count : 0L;
                    long next2 = Math.max(0L, base2 - 1L);
                    c.like_count = next2;
                    if (likeCount != null) likeCount.setText(String.valueOf(next2));
                    if (likeIcon != null) likeIcon.setColorFilter(itemView.getResources().getColor(R.color.text_secondary));
                }

                listener.onToggleDislike(c, target, (disliked, newCount) -> {
                    if (c.dislike_count == null || c.dislike_count.longValue() != newCount) {
                        c.dislike_count = newCount;
                        if (dislikeCount != null) dislikeCount.setText(String.valueOf(newCount));
                    }
                    if (isDisliked != disliked) {
                        isDisliked = disliked;
                        if (dislikeIcon != null) dislikeIcon.setColorFilter(disliked ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
                    }
                    if (disliked && isLiked) {
                        // Clear like if both cannot be active
                        if (likeIcon != null) likeIcon.setColorFilter(itemView.getResources().getColor(R.color.text_secondary));
                        isLiked = false;
                        if (c.like_count != null && c.like_count > 0) c.like_count = c.like_count - 1;
                        if (likeCount != null && c.like_count != null) likeCount.setText(String.valueOf(c.like_count));
                    }
                    dislikeInFlight = false;
                    if (likeContainer != null) likeContainer.setEnabled(true);
                    if (dislikeContainer != null) dislikeContainer.setEnabled(true);
                });
            };
            if (dislikeContainer != null) dislikeContainer.setOnClickListener(dislikeClick);
            if (dislikeIcon != null) dislikeIcon.setOnClickListener(dislikeClick);

            // Three dots menu → Report
            if (menuIcon != null) {
                menuIcon.setOnClickListener(v -> {
                    if (listener != null) listener.onReport(c);
                });
            }
        }
    }
}


