package com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import de.hdodenhof.circleimageview.CircleImageView;


import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private List<ImageItem> items;
    private Context context;

    public ImageAdapter(List<ImageItem> items, Context context) {
        this.items = items;
        this.context = context;
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        CircleImageView imageView;
        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image_profile, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        ImageItem item = items.get(position);
        Glide.with(context).load(item.getImageUrl()).into(holder.imageView);

        holder.imageView.setOnClickListener(v ->
                Toast.makeText(context, "ID: " + item.getId(), Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}

