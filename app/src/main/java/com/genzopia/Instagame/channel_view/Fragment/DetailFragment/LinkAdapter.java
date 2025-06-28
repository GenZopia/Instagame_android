package com.genzopia.Instagame.channel_view.Fragment.DetailFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;

import java.util.List;

public class LinkAdapter extends RecyclerView.Adapter<LinkAdapter.LinkViewHolder> {

    private List<LinkItem> linkList;

    public LinkAdapter(List<LinkItem> linkList) {
        this.linkList = linkList;
    }

    @NonNull
    @Override
    public LinkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_link, parent, false);
        return new LinkViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LinkViewHolder holder, int position) {
        LinkItem item = linkList.get(position);
        holder.titleText.setText(item.getTitle());
        holder.urlText.setText(item.getUrl());
    }

    @Override
    public int getItemCount() {
        return linkList.size();
    }

    static class LinkViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, urlText;

        public LinkViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.titleText);
            urlText = itemView.findViewById(R.id.urlText);
        }
    }
}

