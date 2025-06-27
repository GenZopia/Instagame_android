package com.genzopia.Instagame.channel_view;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.Fragment.DetailFragment.DetailsFragment;
import com.genzopia.Instagame.channel_view.Fragment.GamesFragment.GamesFragment;
import com.genzopia.Instagame.channel_view.Fragment.VideosFragment.VideosFragment;

public class ChannelActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_channel);
        TextView tabGames = findViewById(R.id.tabGames);
        TextView tabVideos = findViewById(R.id.tabVideos);
        TextView tabDetails = findViewById(R.id.tabDetails);

        // Set initial fragment
        loadFragment(new GamesFragment());
        setActiveTab(tabGames);

        // Tab click listeners
        tabGames.setOnClickListener(v -> {
            loadFragment(new GamesFragment());
            setActiveTab(tabGames);
        });

        tabVideos.setOnClickListener(v -> {
            loadFragment(new VideosFragment());
            setActiveTab(tabVideos);
        });

        tabDetails.setOnClickListener(v -> {
            loadFragment(new DetailsFragment());
            setActiveTab(tabDetails);
        });
    }
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
    private void setActiveTab(TextView activeTab) {
        findViewById(R.id.underlineGames).setVisibility(activeTab.getId() == R.id.tabGames ? View.VISIBLE : View.GONE);
        findViewById(R.id.underlineVideos).setVisibility(activeTab.getId() == R.id.tabVideos ? View.VISIBLE : View.GONE);
        findViewById(R.id.underlineDetails).setVisibility(activeTab.getId() == R.id.tabDetails ? View.VISIBLE : View.GONE);
    }

}