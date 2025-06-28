package com.genzopia.Instagame.channel_view.Fragment.DetailFragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;


import java.util.ArrayList;
import java.util.List;

public class DetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinkAdapter adapter;
    private List<LinkItem> linkList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.linksRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Add link items
        linkList = new ArrayList<>();
        linkList.add(new LinkItem("Subscribe", "bit.ly/SharkTankIndiaOfficial"));
        linkList.add(new LinkItem("Facebook", "facebook.com/SonyPicturesTelevisionIndia"));
        linkList.add(new LinkItem("Instagram", "instagram.com/sonypicturestvindia"));

        adapter = new LinkAdapter(linkList);
        recyclerView.setAdapter(adapter);
    }
}
