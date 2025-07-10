package com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class StoryProfileAdapter extends RecyclerView.Adapter<StoryProfileAdapter.ViewHolder> {

    private final List<ImageItem> profileItems;

    public StoryProfileAdapter(List<ImageItem> profileItems) {
        this.profileItems = profileItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_story_profile, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ImageItem item = profileItems.get(position);
        
        // Load profile image using Glide
        Glide.with(holder.itemView.getContext())
                .load(item.getImageUrl())
                .placeholder(R.drawable.profile_pic)
                .error(R.drawable.profile_pic)
                .into(holder.profileImage);

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), 
                "Profile ID: " + item.getId(), 
                Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return profileItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView profileImage;

        ViewHolder(View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profileImage);
        }
    }
} 