package com.genzopia.Instagame.channel_view.Fragment.GamesFragment;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.webgl_gameloading.Game_mode;
import com.google.android.material.button.MaterialButton;

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

        Glide.with(context)
                .load(item.getThumbnailUrl())
                .into(holder.imgThumbnail);

        boolean expanded = item.isExpanded();
        holder.expandedSection.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.imgArrow.setRotation(expanded ? 180f : 0f);

        // Toggle expand/collapse on arrow click
        holder.imgArrow.setOnClickListener(v -> {
            item.setExpanded(!item.isExpanded());
            notifyItemChanged(pos);
        });

        // Also allow tapping the collapsed header area to expand
        holder.itemView.setOnClickListener(v -> {
            if (!item.isExpanded()) {
                item.setExpanded(true);
                notifyItemChanged(pos);
            }
        });

        // Play button — launches Game_mode with the game's ID
        holder.btnPlay.setOnClickListener(v -> {
            String gameId = item.getGameId();
            if (gameId != null && !gameId.isEmpty()) {
                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackChannelGameTapped(
                        "", gameId, item.getTitle()); // developerId not available in adapter
                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackGameLaunchInitiated(
                        gameId, item.getTitle(), "channel_games");
                Intent intent = new Intent(context, Game_mode.class);
                intent.putExtra("game_id", gameId);
                intent.putExtra("game_name", item.getTitle());
                intent.putExtra("launch_source", "channel_games");
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        ImageView     imgThumbnail, imgArrow;
        TextView      tvTitle, tvShortDesc, tvLongDesc;
        LinearLayout  expandedSection;
        MaterialButton btnPlay;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail    = itemView.findViewById(R.id.imgThumbnail);
            imgArrow        = itemView.findViewById(R.id.imgArrow);
            tvTitle         = itemView.findViewById(R.id.tvTitle);
            tvShortDesc     = itemView.findViewById(R.id.tvShortDesc);
            tvLongDesc      = itemView.findViewById(R.id.tvLongDesc);
            expandedSection = itemView.findViewById(R.id.expandedSection);
            btnPlay         = itemView.findViewById(R.id.btnPlay);
        }
    }
}
