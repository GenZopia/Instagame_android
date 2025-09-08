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

public class RepliesAdapter extends ListAdapter<Reply, RepliesAdapter.ReplyVH> {

    public interface OnReplyActionListener {
        void onToggleLike(@NonNull Reply r, boolean like, @NonNull LikeUpdate uiUpdate);
        void checkLiked(@NonNull Reply r, @NonNull LikeState state);
        void onToggleDislike(@NonNull Reply r, boolean dislike, @NonNull LikeUpdate uiUpdate);
        void checkDisliked(@NonNull Reply r, @NonNull LikeState state);
    }

    public interface LikeUpdate { void update(boolean liked, long newCount); }
    public interface LikeState { void setInitial(boolean liked); }

    private final OnReplyActionListener listener;

    public RepliesAdapter() { this(null); }
    public RepliesAdapter(OnReplyActionListener listener) { super(DIFF); setHasStableIds(true); this.listener = listener; }

    private static final DiffUtil.ItemCallback<Reply> DIFF = new DiffUtil.ItemCallback<Reply>() {
        @Override public boolean areItemsTheSame(@NonNull Reply oldItem, @NonNull Reply newItem) {
            return safeEq(oldItem.reply_id, newItem.reply_id);
        }
        @Override public boolean areContentsTheSame(@NonNull Reply oldItem, @NonNull Reply newItem) {
            return safeEq(oldItem.text, newItem.text) && safeEq(oldItem.like_count, newItem.like_count);
        }
        private boolean safeEq(Object a, Object b) { return a == b || (a != null && a.equals(b)); }
    };

    @Override public long getItemId(int position) {
        Reply r = getItem(position);
        return r != null && r.reply_id != null ? r.reply_id.hashCode() : position;
    }

    @NonNull @Override
    public ReplyVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reply, parent, false);
        return new ReplyVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReplyVH holder, int position) { holder.bind(getItem(position)); }

    class ReplyVH extends RecyclerView.ViewHolder {
        ImageView avatar; TextView name; TextView text; TextView meta; View likeContainer; ImageView likeIcon; TextView likeCount; View dislikeContainer; ImageView dislikeIcon; TextView dislikeCount;
        boolean isLiked = false; boolean isDisliked = false; boolean inFlight = false; long lastTapMs = 0L;
        ReplyVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.reply_avatar);
            name = itemView.findViewById(R.id.reply_name);
            text = itemView.findViewById(R.id.reply_text);
            meta = itemView.findViewById(R.id.reply_meta);
            likeContainer = itemView.findViewById(R.id.reply_like_container);
            likeIcon = itemView.findViewById(R.id.reply_like_icon);
            likeCount = itemView.findViewById(R.id.reply_like_count);
            dislikeContainer = itemView.findViewById(R.id.reply_dislike_container);
            dislikeIcon = itemView.findViewById(R.id.reply_dislike_icon);
            dislikeCount = itemView.findViewById(R.id.reply_dislike_count);
        }
        void bind(Reply r) {
            if (r == null) return;
            name.setText(r.user_display_name != null ? r.user_display_name : "");
            text.setText(r.text != null ? r.text : "");
            meta.setText("");
            Glide.with(itemView.getContext()).load(r.user_photo_url).placeholder(R.drawable.demo_user).error(R.drawable.demo_user).into(avatar);

            // counts
            long lc = r.like_count != null ? r.like_count : 0L;
            if (likeCount != null) likeCount.setText(String.valueOf(lc));
            long dc = r.dislike_count != null ? r.dislike_count : 0L;
            if (dislikeCount != null) dislikeCount.setText(String.valueOf(dc));

            if (listener != null) listener.checkLiked(r, liked -> {
                isLiked = liked;
                if (likeIcon != null) likeIcon.setColorFilter(liked ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
            });
            if (listener != null) listener.checkDisliked(r, disliked -> {
                isDisliked = disliked;
                if (dislikeIcon != null) dislikeIcon.setColorFilter(disliked ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
            });

            View.OnClickListener likeClick = v -> {
                if (listener == null) return;
                long now = System.currentTimeMillis();
                if (inFlight || now - lastTapMs < 200) return;
                lastTapMs = now; inFlight = true;
                final boolean target = !isLiked;
                isLiked = target;
                if (likeIcon != null) likeIcon.setColorFilter(target ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
                long base = r.like_count != null ? r.like_count : 0L;
                long next = Math.max(0L, base + (target ? 1L : -1L));
                r.like_count = next; if (likeCount != null) likeCount.setText(String.valueOf(next));
                if (target && isDisliked) {
                    isDisliked = false;
                    long dbase = r.dislike_count != null ? r.dislike_count : 0L;
                    long dnext = Math.max(0L, dbase - 1L);
                    r.dislike_count = dnext; if (dislikeCount != null) dislikeCount.setText(String.valueOf(dnext));
                    if (dislikeIcon != null) dislikeIcon.setColorFilter(itemView.getResources().getColor(R.color.text_secondary));
                }
                listener.onToggleLike(r, target, (ok, newCount) -> {
                    inFlight = false;
                    if (r.like_count == null || r.like_count.longValue() != newCount) { r.like_count = newCount; if (likeCount != null) likeCount.setText(String.valueOf(newCount)); }
                });
            };
            if (likeContainer != null) likeContainer.setOnClickListener(likeClick);
            if (likeIcon != null) likeIcon.setOnClickListener(likeClick);

            View.OnClickListener dislikeClick = v -> {
                if (listener == null) return;
                long now = System.currentTimeMillis();
                if (inFlight || now - lastTapMs < 200) return;
                lastTapMs = now; inFlight = true;
                final boolean target = !isDisliked;
                isDisliked = target;
                if (dislikeIcon != null) dislikeIcon.setColorFilter(target ? itemView.getResources().getColor(R.color.button_primary) : itemView.getResources().getColor(R.color.text_secondary));
                long base = r.dislike_count != null ? r.dislike_count : 0L;
                long next = Math.max(0L, base + (target ? 1L : -1L));
                r.dislike_count = next; if (dislikeCount != null) dislikeCount.setText(String.valueOf(next));
                if (target && isLiked) {
                    isLiked = false;
                    long lbase = r.like_count != null ? r.like_count : 0L;
                    long lnext = Math.max(0L, lbase - 1L);
                    r.like_count = lnext; if (likeCount != null) likeCount.setText(String.valueOf(lnext));
                    if (likeIcon != null) likeIcon.setColorFilter(itemView.getResources().getColor(R.color.text_secondary));
                }
                listener.onToggleDislike(r, target, (ok, newCount) -> {
                    inFlight = false;
                    if (r.dislike_count == null || r.dislike_count.longValue() != newCount) { r.dislike_count = newCount; if (dislikeCount != null) dislikeCount.setText(String.valueOf(newCount)); }
                });
            };
            if (dislikeContainer != null) dislikeContainer.setOnClickListener(dislikeClick);
            if (dislikeIcon != null) dislikeIcon.setOnClickListener(dislikeClick);
        }
    }
}


