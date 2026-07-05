package com.genzopia.Instagame.channel_view.Fragment.GamesFragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.gateway.ChannelGameDTO;
import com.genzopia.Instagame.gateway.ChannelGamesResponse;
import com.genzopia.Instagame.gateway.GatewayClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GamesFragment extends Fragment {

    private RecyclerView rvGames;
    private GameAdapter adapter;
    // Instance-level list — no static cache so each ChannelActivity gets fresh data
    private final List<GameItem> gameList = new ArrayList<>();
    private String developerId;
    private boolean dataLoaded = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_games, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvGames = view.findViewById(R.id.rvGames);
        adapter = new GameAdapter(requireContext(), gameList);
        rvGames.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvGames.setAdapter(adapter);
        if (developerId != null && !dataLoaded) loadGames();
    }

    public void setDeveloperId(String developerId) {
        // Reset if switching developer
        if (!developerId.equals(this.developerId)) {
            this.developerId = developerId;
            dataLoaded = false;
            gameList.clear();
        }
        if (isAdded() && rvGames != null && !dataLoaded) loadGames();
    }

    private void loadGames() {
        if (developerId == null) return;
        GatewayClient.INSTANCE.getCallApi().getChannelGames(developerId)
                .enqueue(new Callback<ChannelGamesResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ChannelGamesResponse> call,
                                           @NonNull Response<ChannelGamesResponse> resp) {
                        if (!isAdded() || !resp.isSuccessful() || resp.body() == null) return;
                        dataLoaded = true;
                        gameList.clear();
                        for (ChannelGameDTO g : resp.body().getData()) {
                            gameList.add(new GameItem(
                                g.getGameId(), g.getGameName(), "", "", g.getGameImageUrl(), ""
                            ));
                        }
                        adapter.notifyDataSetChanged();
                    }
                    @Override
                    public void onFailure(@NonNull Call<ChannelGamesResponse> call, @NonNull Throwable t) {
                        Log.e("GamesFragment", "load failed: " + t.getMessage());
                    }
                });
    }
}
