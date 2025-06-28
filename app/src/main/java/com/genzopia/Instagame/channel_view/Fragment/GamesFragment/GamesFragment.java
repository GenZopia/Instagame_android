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

import java.util.ArrayList;
import java.util.List;

public class GamesFragment extends Fragment {

    private RecyclerView rvGames;
    private GameAdapter adapter;
    private List<GameItem> gameList;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the fragment layout that contains your RecyclerView:
        // e.g. <RecyclerView android:id="@+id/rvGames" … />
        return inflater.inflate(R.layout.fragment_games, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1) Find RecyclerView
        rvGames = view.findViewById(R.id.rvGames);
        Log.e("test111","done");

        // 2) Prepare data
        gameList = new ArrayList<>();
        gameList.add(new GameItem(
                "Accessibility permission",
                "Google Play Console",
                "When you upload to the Play Console you need to request the ACCESSIBILITY_SERVICE permission if your app uses any accessibility APIs...",
                "https://mir-s3-cdn-cf.behance.net/project_modules/1400/50627a175474311.64b4b17cb24b3.jpg", // Use a real placeholder image
                "https://mir-s3-cdn-cf.behance.net/project_modules/1400/50627a175474311.64b4b17cb24b3.jpg"
        ));
        gameList.add(new GameItem(
                "Accessibility permission",
                "Google Play Console",
                "When you upload to the Play Console you need to request the ACCESSIBILITY_SERVICE permission if your app uses any accessibility APIs...",
                "https://res.cloudinary.com/upwork-cloud/image/upload/c_scale,w_1000/v1709867164/catalog/1412115037622841344/afjq0smgngmlb97qxafe.webp", // Use a real placeholder image
                "https://play.google.com/store/apps/details?id=com.example.game1"
        ));
        // …add as many as you like

        // 3) Create adapter
        adapter = new GameAdapter(requireContext(), gameList);

        // 4) Set LayoutManager and Adapter
        rvGames.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvGames.setAdapter(adapter);
        Log.e("test112","done");
    }
}
