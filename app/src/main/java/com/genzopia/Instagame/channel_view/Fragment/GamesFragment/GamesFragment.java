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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GamesFragment extends Fragment {

    private RecyclerView rvGames;
    private GameAdapter adapter;
    private List<GameItem> gameList;
    private String developerId;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_games, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1) Find RecyclerView
        rvGames = view.findViewById(R.id.rvGames);
        Log.d("GamesFragment", "Fragment created");

        // 2) Prepare data
        gameList = new ArrayList<>();

        // 3) Create adapter
        adapter = new GameAdapter(requireContext(), gameList);

        // 4) Set LayoutManager and Adapter
        rvGames.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvGames.setAdapter(adapter);
        
        // 5) Load games if developer ID is set
        if (developerId != null) {
            loadGamesFromFirebase();
        }
    }
    
    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
        if (isAdded() && rvGames != null) {
            loadGamesFromFirebase();
        }
    }
    
    private void loadGamesFromFirebase() {
        if (developerId == null) {
            Log.e("GamesFragment", "Developer ID is null");
            return;
        }
        
        Log.d("GamesFragment", "Loading games for developer: " + developerId);
        
        // First, get the developer's games list
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(developerId).child("games");
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gameList.clear();
                
                Log.d("GamesFragment", "Games snapshot exists: " + snapshot.exists());
                Log.d("GamesFragment", "Games snapshot children count: " + snapshot.getChildrenCount());
                
                if (snapshot.exists()) {
                    // Iterate through the developer's games
                    for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                        String gameId = gameSnapshot.getKey();
                        Log.d("GamesFragment", "Found game ID: " + gameId);
                        if (gameId != null) {
                            // Fetch game details from games collection
                            loadGameDetails(gameId);
                        }
                    }
                } else {
                    Log.d("GamesFragment", "No games found for developer: " + developerId);
                    adapter.notifyDataSetChanged();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GamesFragment", "Error loading developer games: " + error.getMessage());
            }
        });
    }
    
    private void loadGameDetails(String gameId) {
        Log.d("GamesFragment", "Loading game details for game ID: " + gameId);
        
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("GamesFragment", "Game details snapshot exists: " + snapshot.exists());
                
                if (snapshot.exists()) {
                    String gameName = snapshot.child("game_name").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    String imageUrl = snapshot.child("image_url").getValue(String.class);
                    String playStoreUrl = snapshot.child("play_store_url").getValue(String.class);
                    
                    Log.d("GamesFragment", "Game name: " + gameName);
                    Log.d("GamesFragment", "Game description: " + description);
                    Log.d("GamesFragment", "Game image URL: " + imageUrl);
                    Log.d("GamesFragment", "Game Play Store URL: " + playStoreUrl);
                    
                    // Create GameItem with fetched data
                    GameItem gameItem = new GameItem(
                        gameName != null ? gameName : "Unknown Game",
                        "Developer Game",
                        description != null ? description : "No description available",
                        imageUrl != null ? imageUrl : "",
                        playStoreUrl != null ? playStoreUrl : ""
                    );
                    
                    gameList.add(gameItem);
                    adapter.notifyDataSetChanged();
                    
                    Log.d("GamesFragment", "Added game: " + gameName + " (Total games in list: " + gameList.size() + ")");
                } else {
                    Log.e("GamesFragment", "Game details not found for game ID: " + gameId);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GamesFragment", "Error loading game details: " + error.getMessage());
            }
        });
    }
}
