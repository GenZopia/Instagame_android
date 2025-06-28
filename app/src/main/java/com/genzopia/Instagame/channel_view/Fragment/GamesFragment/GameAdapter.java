package com.genzopia.Instagame.channel_view.Fragment.GamesFragment;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;

import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

    private final Context context;
    private final List<GameItem> items;

    public GameAdapter(Context context, List<GameItem> items) {
        this.context = context;
        this.items   = items;
    }

    @NonNull @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_game, parent, false);
        return new GameViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int pos) {
        GameItem item = items.get(pos);

        holder.tvTitle.setText(item.getTitle());
        holder.tvShortDesc.setText(item.getShortDescription());
        holder.tvLongDesc.setText(item.getLongDescription());

        // Use a proper placeholder and error image
        Glide.with(context)
                .load(item.getThumbnailUrl())
                .into(holder.imgThumbnail);

        boolean expanded = item.isExpanded();
        holder.tvLongDesc.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.imgArrow.setRotation(expanded ? 180f : 0f);
        holder.imgArrow.setOnClickListener(v->{
            item.setExpanded(!item.isExpanded());
            notifyItemChanged(pos);
        });

        // Add click listener to the whole item if you want
        holder.itemView.setOnClickListener(v -> {
            Toast.makeText(context,item.getGameLink(), LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail, imgArrow;
        TextView  tvTitle, tvShortDesc, tvLongDesc;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            imgArrow     = itemView.findViewById(R.id.imgArrow);
            tvTitle      = itemView.findViewById(R.id.tvTitle);
            tvShortDesc  = itemView.findViewById(R.id.tvShortDesc);
            tvLongDesc   = itemView.findViewById(R.id.tvLongDesc);
        }
    }
}
