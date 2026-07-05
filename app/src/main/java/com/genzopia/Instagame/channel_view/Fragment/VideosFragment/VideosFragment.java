package com.genzopia.Instagame.channel_view.Fragment.VideosFragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.gateway.GatewayClient;
import com.genzopia.Instagame.gateway.ReelDTO;
import com.genzopia.Instagame.gateway.ReelsPageResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private final List<VideoItem_channel> videoList = new ArrayList<>();
    private String developerId;
    private boolean dataLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_videos, container, false);
        recyclerView = view.findViewById(R.id.videosRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new VideoAdapter(getContext(), videoList);
        recyclerView.setAdapter(adapter);
        if (developerId != null && !dataLoaded) loadVideos();
        return view;
    }

    public void setDeveloperId(String developerId) {
        if (!developerId.equals(this.developerId)) {
            this.developerId = developerId;
            dataLoaded = false;
            videoList.clear();
        }
        if (isAdded() && recyclerView != null && !dataLoaded) loadVideos();
    }

    private void loadVideos() {
        if (developerId == null) return;
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        final String currentUid = u != null ? u.getUid() : "";

        GatewayClient.INSTANCE.getCallApi().getChannelVideos(developerId)
                .enqueue(new Callback<ReelsPageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ReelsPageResponse> call,
                                           @NonNull Response<ReelsPageResponse> resp) {
                        if (!isAdded() || !resp.isSuccessful() || resp.body() == null) return;
                        dataLoaded = true;
                        videoList.clear();
                        boolean isOwn = currentUid.equals(developerId);
                        for (ReelDTO v : resp.body().getData()) {
                            videoList.add(new VideoItem_channel(
                                v.getVideoId(), "", v.getViewCount() + " views",
                                v.getTitle(), true, isOwn
                            ));
                        }
                        adapter.notifyDataSetChanged();
                    }
                    @Override
                    public void onFailure(@NonNull Call<ReelsPageResponse> call, @NonNull Throwable t) {
                        Log.e("VideosFragment", "load failed: " + t.getMessage());
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) adapter.releaseResources();
    }
}
