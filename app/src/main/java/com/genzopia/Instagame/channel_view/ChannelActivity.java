package com.genzopia.Instagame.channel_view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.common.BaseActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.Fragment.DetailFragment.DetailsFragment;
import com.genzopia.Instagame.channel_view.Fragment.GamesFragment.GamesFragment;
import com.genzopia.Instagame.channel_view.Fragment.VideosFragment.VideosFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChannelActivity extends BaseActivity {

    private String developerId;
    private CircleImageView profileImage;
    private TextView channelName;
    private TextView subscriberCount;
    private ImageView bannerImage;
    private Button followButton;
    private boolean isFollowing = false;
    private GamesFragment gamesFragment;
    private VideosFragment videosFragment;
    private DetailsFragment detailsFragment;
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_channel);

        developerId = getIntent().getStringExtra("developer_id");
        if (developerId == null) {
            Log.e("ChannelActivity", "No developer ID provided");
            finish();
            return;
        }

        profileImage    = findViewById(R.id.profileImage);
        channelName     = findViewById(R.id.channelName);
        subscriberCount = findViewById(R.id.subscriberCount);
        bannerImage     = findViewById(R.id.bannerImage);
        followButton    = findViewById(R.id.followButton);

        TextView tabGames   = findViewById(R.id.tabGames);
        TextView tabVideos  = findViewById(R.id.tabVideos);
        TextView tabDetails = findViewById(R.id.tabDetails);

        loadDeveloperData();
        initializeFragments();
        loadFragment(gamesFragment);
        setActiveTab(tabGames);

        tabGames.setOnClickListener(v -> { loadFragment(gamesFragment); setActiveTab(tabGames); });
        tabVideos.setOnClickListener(v -> { loadFragment(videosFragment); setActiveTab(tabVideos); });
        tabDetails.setOnClickListener(v -> { loadFragment(detailsFragment); setActiveTab(tabDetails); });

        setupFollowButton();
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
    
    private void setupFollowButton() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUid == null || currentUid.equals(developerId)) {
            if (followButton != null) followButton.setVisibility(View.GONE);
            return;
        }
        // Check current follow state
        FirebaseDatabase.getInstance().getReference("users").child(currentUid)
                .child("following_list").child(developerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot snapshot) {
                        isFollowing = snapshot.exists();
                        updateFollowButton();
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });

        followButton.setOnClickListener(v -> {
            if (currentUid == null) return;
            isFollowing = !isFollowing;
            updateFollowButton();
            DatabaseReference followRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(currentUid).child("following_list").child(developerId);
            DatabaseReference followersCountRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(developerId).child("followers_count");
            if (isFollowing) {
                followRef.setValue(true);
                followersCountRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot s) {
                        long count = s.getValue(Long.class) != null ? s.getValue(Long.class) : 0L;
                        followersCountRef.setValue(count + 1);
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
            } else {
                followRef.removeValue();
                followersCountRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot s) {
                        long count = s.getValue(Long.class) != null ? s.getValue(Long.class) : 1L;
                        followersCountRef.setValue(Math.max(0, count - 1));
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
            }
        });
    }

    private void updateFollowButton() {
        if (followButton == null) return;
        if (isFollowing) {
            followButton.setText("Following");
            followButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.text_secondary, getTheme())));
        } else {
            followButton.setText("Follow");
            followButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.instagram_orange, getTheme())));
        }
    }

    private void loadDeveloperData() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(developerId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    channelName.setText("Developer Not Found");
                    subscriberCount.setText("0 followers");
                    return;
                }

                // Profile photo
                String profilePhotoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                String sanitizedPhotoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(profilePhotoUrl);
                if (sanitizedPhotoUrl != null) {
                    Glide.with(ChannelActivity.this).load(sanitizedPhotoUrl)
                            .placeholder(R.drawable.demo_user).error(R.drawable.demo_user)
                            .into(profileImage);
                }

                // Banner photo
                String bannerUrl = snapshot.child("banner_url").getValue(String.class);
                if (bannerUrl != null && !bannerUrl.isEmpty() && bannerImage != null) {
                    Glide.with(ChannelActivity.this).load(bannerUrl)
                            .centerCrop().into(bannerImage);
                }

                // Name
                String fullName = snapshot.child("full_name").getValue(String.class);
                channelName.setText(fullName != null && !fullName.isEmpty() ? fullName : "Unknown Developer");

                // Follower count
                Long followersCount = snapshot.child("followers_count").getValue(Long.class);
                if (followersCount == null) {
                    // fallback to old field
                    String followersStr = snapshot.child("followers").getValue(String.class);
                    try { followersCount = followersStr != null ? Long.parseLong(followersStr) : 0L; }
                    catch (NumberFormatException e) { followersCount = 0L; }
                }

                int videoCount = snapshot.child("videos").exists()
                        ? (int) snapshot.child("videos").getChildrenCount() : 0;
                int gameCount = snapshot.child("games").exists()
                        ? (int) snapshot.child("games").getChildrenCount() : 0;

                subscriberCount.setText(formatCount(followersCount.intValue()) + " followers  •  "
                        + videoCount + " videos  •  " + gameCount + " games");
            }

            @Override
            public void onCancelled(DatabaseError error) {
                channelName.setText("Error Loading");
                subscriberCount.setText("0 followers");
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