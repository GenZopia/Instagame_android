package com.genzopia.Instagame.channel_view.Fragment.VideosFragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;


import java.util.ArrayList;
import java.util.List;

public class VideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<VideoItem_channel> videoList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_videos, container, false);

        recyclerView = view.findViewById(R.id.videosRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3, RecyclerView.VERTICAL, false));

        // Initialize video list
        videoList = new ArrayList<>();
        // Real YouTube Shorts examples:
        videoList.add(new VideoItem_channel("hKwrn5-7FjQ",
                "https://i.ytimg.com/vi/hKwrn5-7FjQ/maxresdefault.jpg", "2.3M views"));
        videoList.add(new VideoItem_channel("pKML4pZozDY",
                "https://i.ytimg.com/vi/pKML4pZozDY/maxresdefault.jpg", "1.1M views"));
        videoList.add(new VideoItem_channel("dQw4w9WgXcQ",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", "900K views"));
        // Additional real or sample Shorts:
        videoList.add(new VideoItem_channel("B-s71n0dHUk",
                "https://i.ytimg.com/vi/B-s71n0dHUk/maxresdefault.jpg", "3.4M views"));
        videoList.add(new VideoItem_channel("ydPkyvWtmg4",
                "https://i.ytimg.com/vi/ydPkyvWtmg4/maxresdefault.jpg", "750K views"));


        adapter = new VideoAdapter(getContext(), videoList);
        recyclerView.setAdapter(adapter);

        return view;
    }
}