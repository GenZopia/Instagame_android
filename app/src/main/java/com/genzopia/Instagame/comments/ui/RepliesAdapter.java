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
import com.genzopia.Instagame.comments.models.Reply;

/** Like-only replies adapter — no dislike. */
public class RepliesAdapter extends ListAdapter<Reply, RepliesAdapter.ReplyVH> {

    public interface OnReplyActionListener {
        void onToggleLike(@NonNull Reply r);
        void checkLiked(@NonNull Reply r, @NonNull LikeState state);
    }

    public interface LikeState { void setInitial(boolean liked); }

    private final OnReplyActionListener listener;

    public RepliesAdapter() { this(null); }
    public RepliesAdapter(OnReplyActionListener listener) {
        super(DIFF);
        setHasStableIds(true);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Reply> DIFF = new DiffUtil.ItemCallback<Reply>() {
        @Override public boolean areItemsTheSame(@NonNull Reply a, @NonNull Reply b) {
            return a.reply_id != null && a.reply_id.equals(b.reply_id);
        }
        @Override public boolean areContentsTheSame(@NonNull Reply a, @NonNull Reply b) {
            return eq(a.text, b.text) && eq(a.like_count, b.like_count);
        }
        private boolean eq(Object a, Object b) { return a == b || (a != null && a.equals(b)); }
    };

    @Override public long getItemId(int pos) {
        Reply r = getItem(pos);
        return r != null && r.reply_id != null ? r.reply_id.hashCode() : pos;
    }

    @NonNull @Override
    public ReplyVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reply, parent, false);
        return new ReplyVH(v);
    }

    @Override public void onBindViewHolder(@NonNull ReplyVH h, int pos) { h.bind(getItem(pos)); }

    class ReplyVH extends RecyclerView.ViewHolder {
        ImageView avatar, likeIcon;
        TextView name, text, meta, likeCount;
        View likeContainer, dislikeContainer;

        // Local like state — toggled instantly on click
        private boolean localLiked = false;
        private long localLikeCount = 0L;
        private String boundReplyId = null;

        ReplyVH(@NonNull View v) {
            super(v);
            avatar          = v.findViewById(R.id.reply_avatar);
            name            = v.findViewById(R.id.reply_name);
            text            = v.findViewById(R.id.reply_text);
            meta            = v.findViewById(R.id.reply_meta);
            likeContainer   = v.findViewById(R.id.reply_like_container);
            likeIcon        = v.findViewById(R.id.reply_like_icon);
            likeCount       = v.findViewById(R.id.reply_like_count);
            dislikeContainer= v.findViewById(R.id.reply_dislike_container);
        }

        void bind(Reply r) {
            if (r == null) return;

            // Hide dislike
            if (dislikeContainer != null) dislikeContainer.setVisibility(View.GONE);

            name.setText(r.user_display_name != null ? r.user_display_name : "");
            text.setText(r.text != null ? r.text : "");
            if (meta != null) meta.setText("");

            Glide.with(itemView.getContext())
                    .load(r.user_photo_url)
                    .placeholder(R.drawable.demo_user)
                    .error(R.drawable.demo_user)
                    .into(avatar);

            // Only reset local state when binding a NEW reply
            boolean isNewReply = !r.reply_id.equals(boundReplyId);
            if (isNewReply) {
                boundReplyId = r.reply_id;
                localLikeCount = r.like_count != null ? r.like_count : 0L;
                if (listener != null) listener.checkLiked(r, liked -> {
                    localLiked = liked;
                    updateLikeIcon(localLiked);
                });
            }

            if (likeCount != null) likeCount.setText(localLikeCount > 0 ? String.valueOf(localLikeCount) : "");
            updateLikeIcon(localLiked);

            View.OnClickListener likeClick = v -> {
                localLiked = !localLiked;
                localLikeCount = Math.max(0L, localLikeCount + (localLiked ? 1L : -1L));
                updateLikeIcon(localLiked);
                if (likeCount != null) likeCount.setText(localLikeCount > 0 ? String.valueOf(localLikeCount) : "");
                if (listener != null) listener.onToggleLike(r);
            };
            if (likeContainer != null) likeContainer.setOnClickListener(likeClick);
            if (likeIcon != null) likeIcon.setOnClickListener(likeClick);
        }

        private void updateLikeIcon(boolean liked) {
            if (likeIcon == null) return;
            likeIcon.setColorFilter(liked
                    ? itemView.getResources().getColor(R.color.button_primary)
                    : itemView.getResources().getColor(R.color.text_secondary));
        }
    }
}
