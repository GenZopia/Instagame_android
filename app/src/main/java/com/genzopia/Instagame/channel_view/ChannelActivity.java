package com.genzopia.Instagame.channel_view;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.Fragment.DetailFragment.DetailsFragment;
import com.genzopia.Instagame.channel_view.Fragment.GamesFragment.GamesFragment;
import com.genzopia.Instagame.channel_view.Fragment.VideosFragment.VideosFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChannelActivity extends AppCompatActivity {

    private String developerId;
    private CircleImageView profileImage;
    private TextView channelName;
    private TextView subscriberCount;
    private ImageView bannerImage;
    
    // Fragment management
    private GamesFragment gamesFragment;
    private VideosFragment videosFragment;
    private DetailsFragment detailsFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_channel);
        
        // Get developer ID from intent
        developerId = getIntent().getStringExtra("developer_id");
        if (developerId == null) {
            Log.e("ChannelActivity", "No developer ID provided");
            finish();
            return;
        }
        
        // Initialize views
        profileImage = findViewById(R.id.profileImage);
        channelName = findViewById(R.id.channelName);
        subscriberCount = findViewById(R.id.subscriberCount);
        bannerImage = findViewById(R.id.bannerImage);
        
        TextView tabGames = findViewById(R.id.tabGames);
        TextView tabVideos = findViewById(R.id.tabVideos);
        TextView tabDetails = findViewById(R.id.tabDetails);

        // Load developer data
        loadDeveloperData();

        // Initialize fragments
        initializeFragments();

        // Set initial fragment
        loadFragment(gamesFragment);
        setActiveTab(tabGames);

        // Tab click listeners
        tabGames.setOnClickListener(v -> {
            loadFragment(gamesFragment);
            setActiveTab(tabGames);
        });

        tabVideos.setOnClickListener(v -> {
            loadFragment(videosFragment);
            setActiveTab(tabVideos);
        });

        tabDetails.setOnClickListener(v -> {
            loadFragment(detailsFragment);
            setActiveTab(tabDetails);
        });
    }
    
    private void initializeFragments() {
        // Create fragments only once
        gamesFragment = new GamesFragment();
        videosFragment = new VideosFragment();
        detailsFragment = new DetailsFragment();
        
        // Set developer ID for all fragments
        gamesFragment.setDeveloperId(developerId);
        videosFragment.setDeveloperId(developerId);
        detailsFragment.setDeveloperId(developerId);
        
        // Add all fragments to container but hide them initially
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, gamesFragment)
                .add(R.id.fragmentContainer, videosFragment)
                .add(R.id.fragmentContainer, detailsFragment)
                .hide(videosFragment)
                .hide(detailsFragment)
                .commit();
        
        currentFragment = gamesFragment;
    }
    
    private void loadDeveloperData() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(developerId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Load profile image
                    String profilePhotoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                    if (profilePhotoUrl != null && !profilePhotoUrl.isEmpty()) {
                        Glide.with(ChannelActivity.this)
                                .load(profilePhotoUrl)
                                .placeholder(R.drawable.demo_user)
                                .error(R.drawable.demo_user)
                                .into(profileImage);
                    } else {
                        profileImage.setImageResource(R.drawable.demo_user);
                    }
                    
                    // Load channel name
                    String fullName = snapshot.child("full_name").getValue(String.class);
                    if (fullName != null && !fullName.isEmpty()) {
                        channelName.setText(fullName);
                    } else {
                        channelName.setText("Unknown Developer");
                    }
                    
                    // Load subscriber count and video count
                    String followers = snapshot.child("followers").getValue(String.class);
                    int followerCount = 0;
                    if (followers != null && !followers.isEmpty()) {
                        try {
                            followerCount = Integer.parseInt(followers);
                        } catch (NumberFormatException e) {
                            Log.e("ChannelActivity", "Error parsing follower count: " + followers);
                        }
                    }
                    
                    // Count videos - check if videos node exists and count its children
                    DataSnapshot videosSnapshot = snapshot.child("videos");
                    int videoCount = 0;
                    if (videosSnapshot.exists()) {
                        videoCount = (int) videosSnapshot.getChildrenCount();
                        Log.d("ChannelActivity", "Found " + videoCount + " videos for developer: " + developerId);
                        // Debug: log all video IDs
                        for (DataSnapshot videoSnapshot : videosSnapshot.getChildren()) {
                            Log.d("ChannelActivity", "Video ID: " + videoSnapshot.getKey());
                        }
                    } else {
                        Log.d("ChannelActivity", "No videos node found for developer: " + developerId);
                    }
                    
                    // Count games - check if games node exists and count its children
                    DataSnapshot gamesSnapshot = snapshot.child("games");
                    int gameCount = 0;
                    if (gamesSnapshot.exists()) {
                        gameCount = (int) gamesSnapshot.getChildrenCount();
                        Log.d("ChannelActivity", "Found " + gameCount + " games for developer: " + developerId);
                        // Debug: log all game IDs
                        for (DataSnapshot gameSnapshot : gamesSnapshot.getChildren()) {
                            Log.d("ChannelActivity", "Game ID: " + gameSnapshot.getKey());
                        }
                    } else {
                        Log.d("ChannelActivity", "No games node found for developer: " + developerId);
                    }
                    
                    // Format subscriber count
                    String formattedCount = formatCount(followerCount);
                    subscriberCount.setText(formattedCount + " followers • " + videoCount + " videos • " + gameCount + " games");
                    
                    Log.d("ChannelActivity", "Loaded developer data: " + fullName + ", " + followerCount + " followers, " + videoCount + " videos, " + gameCount + " games");
                } else {
                    Log.e("ChannelActivity", "Developer not found: " + developerId);
                    channelName.setText("Developer Not Found");
                    subscriberCount.setText("0 followers • 0 videos • 0 games");
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("ChannelActivity", "Error loading developer data: " + error.getMessage());
                channelName.setText("Error Loading Data");
                subscriberCount.setText("0 followers • 0 videos • 0 games");
            }
        });
    }
    
    private String formatCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format("%.1fK", count / 1000.0);
        } else {
            return String.format("%.1fM", count / 1000000.0);
        }
    }
    
    public String getDeveloperId() {
        return developerId;
    }
    
    private void loadFragment(Fragment fragment) {
        // Hide the current fragment
        if (currentFragment != null && currentFragment != fragment) {
            getSupportFragmentManager().beginTransaction()
                    .hide(currentFragment)
                    .show(fragment)
                    .commit();
        } else if (currentFragment == null) {
            // First time loading
            getSupportFragmentManager().beginTransaction()
                    .show(fragment)
                    .commit();
        }
        
        currentFragment = fragment;
    }
    
    private void setActiveTab(TextView activeTab) {
        findViewById(R.id.underlineGames).setVisibility(activeTab.getId() == R.id.tabGames ? View.VISIBLE : View.GONE);
        findViewById(R.id.underlineVideos).setVisibility(activeTab.getId() == R.id.tabVideos ? View.VISIBLE : View.GONE);
        findViewById(R.id.underlineDetails).setVisibility(activeTab.getId() == R.id.tabDetails ? View.VISIBLE : View.GONE);
    }
}