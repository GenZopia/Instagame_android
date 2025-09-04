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

    public RepliesAdapter() { super(DIFF); setHasStableIds(true); }

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
        ImageView avatar; TextView name; TextView text; TextView meta;
        ReplyVH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.reply_avatar);
            name = itemView.findViewById(R.id.reply_name);
            text = itemView.findViewById(R.id.reply_text);
            meta = itemView.findViewById(R.id.reply_meta);
        }
        void bind(Reply r) {
            if (r == null) return;
            name.setText(r.user_display_name != null ? r.user_display_name : "");
            text.setText(r.text != null ? r.text : "");
            meta.setText("");
            Glide.with(itemView.getContext()).load(r.user_photo_url).placeholder(R.drawable.demo_user).error(R.drawable.demo_user).into(avatar);
        }
    }
}


