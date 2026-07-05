package com.genzopia.Instagame.channel_view.Fragment.DetailFragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.gateway.ChannelDTO;
import com.genzopia.Instagame.gateway.GatewayClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinkAdapter adapter;
    private final List<LinkItem> linkList = new ArrayList<>();
    private String developerId;

    private TextView tvBio, tvFollowersCount, tvVideosCount, tvGamesCount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.linksRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LinkAdapter(linkList);
        recyclerView.setAdapter(adapter);

        if (developerId != null) loadDetails();
    }

    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
        linkList.clear();
        if (isAdded() && recyclerView != null) loadDetails();
    }

    private void loadDetails() {
        if (developerId == null) return;
        GatewayClient.INSTANCE.getCallApi().getChannel(developerId)
                .enqueue(new Callback<ChannelDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ChannelDTO> call,
                                           @NonNull Response<ChannelDTO> resp) {
                        if (!isAdded() || !resp.isSuccessful() || resp.body() == null) return;
                        ChannelDTO ch = resp.body();

                        if (tvBio != null) {
                            String bio = ch.getBio();
                            tvBio.setText(bio != null && !bio.isEmpty() ? bio : "No bio yet.");
                        }
                        if (tvFollowersCount != null)
                            tvFollowersCount.setText(formatCount((int) ch.getFollowersCount()));
                        if (tvVideosCount != null)
                            tvVideosCount.setText(String.valueOf(ch.getVideoCount()));
                        if (tvGamesCount != null)
                            tvGamesCount.setText(String.valueOf(ch.getGameCount()));

                        linkList.clear();
                        if (ch.getWebsite() != null && !ch.getWebsite().isEmpty())
                            linkList.add(new LinkItem("Website", ch.getWebsite()));
                        if (ch.getStory() != null && !ch.getStory().isEmpty())
                            linkList.add(new LinkItem("Story", ch.getStory()));

                        adapter.notifyDataSetChanged();
                    }
                    @Override
                    public void onFailure(@NonNull Call<ChannelDTO> call, @NonNull Throwable t) {}
                });
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }
}
