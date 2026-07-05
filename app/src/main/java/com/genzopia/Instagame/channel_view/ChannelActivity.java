package com.genzopia.Instagame.channel_view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.common.BaseActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.Fragment.DetailFragment.DetailsFragment;
import com.genzopia.Instagame.channel_view.Fragment.GamesFragment.GamesFragment;
import com.genzopia.Instagame.channel_view.Fragment.VideosFragment.VideosFragment;
import com.genzopia.Instagame.gateway.ChannelDTO;
import com.genzopia.Instagame.gateway.FollowResponse;
import com.genzopia.Instagame.gateway.GatewayClient;
import com.google.firebase.auth.FirebaseAuth;

import androidx.annotation.NonNull;
import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChannelActivity extends BaseActivity {

    private String developerId;
    private CircleImageView profileImage;
    private TextView channelName;
    private TextView subscriberCount;
    private TextView channelWebsite;
    private TextView channelStory;
    private ImageView bannerImage;
    private Button followButton;
    private boolean isFollowing = false;
    private GamesFragment gamesFragment;
    private VideosFragment videosFragment;
    private DetailsFragment detailsFragment;
    private Fragment currentFragment;
    // True once we've painted the avatar passed in by the caller. When set, we
    // don't let the async loadDeveloperData() response overwrite the already
    // visible, correct profile image.
    private boolean profilePhotoShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        channelWebsite  = findViewById(R.id.channelWebsite);
        channelStory    = findViewById(R.id.channelStory);

        TextView tabGames   = findViewById(R.id.tabGames);
        TextView tabVideos  = findViewById(R.id.tabVideos);
        TextView tabDetails = findViewById(R.id.tabDetails);

        // Efficiency: if the caller (home / reel view) already resolved the
        // developer's avatar URL, reuse it to paint the profile image instantly.
        // Because it is the exact same gateway URL, Glide serves it from cache and
        // no additional network request is made.
        String preloadedPhoto = getIntent().getStringExtra("developer_photo_url");
        if (preloadedPhoto != null && !preloadedPhoto.isEmpty()) {
            // Render with the same Coil ImageLoader the caller (reel / home / etc.)
            // used, so it loads reliably and is served from Coil's cache.
            loadProfilePhoto(preloadedPhoto);
            profilePhotoShown = true;
        }

        loadDeveloperData();
        initializeFragments();
        loadFragment(gamesFragment);
        setActiveTab(tabGames);

        // Track channel viewed — source passed via intent, default to "unknown"
        String channelSource = getIntent().getStringExtra("channel_source");
        // Name resolved after loadDeveloperData; we track with a placeholder and
        // update once the name arrives via the listener in loadDeveloperData()
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackChannelViewed(
                developerId,
                "",  // name filled in loadDeveloperData callback
                channelSource != null ? channelSource : "unknown"
        );
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("channel_" + developerId);

        tabGames.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackChannelTabSwitched(developerId, "games");
            loadFragment(gamesFragment);
            setActiveTab(tabGames);
        });
        tabVideos.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackChannelTabSwitched(developerId, "videos");
            loadFragment(videosFragment);
            setActiveTab(tabVideos);
        });
        tabDetails.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackChannelTabSwitched(developerId, "details");
            loadFragment(detailsFragment);
            setActiveTab(tabDetails);
        });

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

        followButton.setOnClickListener(v -> {
            isFollowing = !isFollowing;
            updateFollowButton();
            String devName = channelName != null ? channelName.getText().toString() : "";
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackChannelFollowTapped(
                    developerId, devName, isFollowing ? "follow" : "unfollow");

            Call<FollowResponse> call = isFollowing
                    ? GatewayClient.INSTANCE.getCallApi().followUser(developerId)
                    : GatewayClient.INSTANCE.getCallApi().unfollowUser(developerId);

            call.enqueue(new Callback<FollowResponse>() {
                @Override
                public void onResponse(@NonNull Call<FollowResponse> c,
                                       @NonNull Response<FollowResponse> resp) {
                    if (!resp.isSuccessful()) {
                        Log.e("ChannelActivity", "follow/unfollow HTTP " + resp.code());
                    }
                }
                @Override
                public void onFailure(@NonNull Call<FollowResponse> c, @NonNull Throwable t) {
                    Log.e("ChannelActivity", "follow/unfollow failed", t);
                }
            });
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
        GatewayClient.INSTANCE.getCallApi().getChannel(developerId)
                .enqueue(new Callback<ChannelDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<ChannelDTO> call,
                                           @NonNull Response<ChannelDTO> resp) {
                        if (!resp.isSuccessful() || resp.body() == null) {
                            channelName.setText("Error Loading");
                            subscriberCount.setText("0 followers");
                            return;
                        }
                        ChannelDTO ch = resp.body();
                        isFollowing = ch.isFollowing();

                        // Only load here if the caller didn't already hand us the
                        // avatar — avoids overwriting the visible image with a second
                        // fetch (which is what caused the intermittent placeholder).
                        if (!profilePhotoShown) {
                            String sanitizedPhoto = com.genzopia.Instagame.utils.ProfilePhotoUtils
                                    .sanitize(ch.getProfilePhotoUrl());
                            if (sanitizedPhoto != null) {
                                loadProfilePhoto(sanitizedPhoto);
                                profilePhotoShown = true;
                            }
                        }
                        if (ch.getBannerUrl() != null && !ch.getBannerUrl().isEmpty()
                                && bannerImage != null) {
                            Glide.with(ChannelActivity.this).load(ch.getBannerUrl())
                                    .centerCrop().into(bannerImage);
                        }

                        String name = (ch.getFullName() != null && !ch.getFullName().isEmpty())
                                ? ch.getFullName() : "Unknown Developer";
                        channelName.setText(name);
                        subscriberCount.setText(formatCount((int) ch.getFollowersCount())
                                + " followers  •  " + ch.getVideoCount()
                                + " videos  •  " + ch.getGameCount() + " games");

                        if (ch.getWebsite() != null && !ch.getWebsite().isEmpty()) {
                            channelWebsite.setText(ch.getWebsite());
                            channelWebsite.setVisibility(android.view.View.VISIBLE);
                        }
                        if (ch.getStory() != null && !ch.getStory().isEmpty()) {
                            channelStory.setText(ch.getStory());
                            channelStory.setVisibility(android.view.View.VISIBLE);
                        }
                        updateFollowButton();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ChannelDTO> call, @NonNull Throwable t) {
                        Log.e("ChannelActivity", "loadDeveloperData failed", t);
                        channelName.setText("Error Loading");
                        subscriberCount.setText("0 followers");
                    }
                });
    }
    
    /**
     * Loads a profile photo into {@link #profileImage} using the app-wide Coil
     * ImageLoader configured in MyApplication (which injects x-api-key + Bearer
     * for gateway media URLs). This is the same loader the reel / home screens
     * use, so the image renders consistently and is served from cache.
     */
    private void loadProfilePhoto(String url) {
        if (url == null || url.isEmpty() || profileImage == null) return;
        coil.request.ImageRequest request = new coil.request.ImageRequest.Builder(ChannelActivity.this)
                .data(url)
                .placeholder(R.drawable.demo_user)
                .error(R.drawable.demo_user)
                .target(profileImage)
                .build();
        coil.Coil.imageLoader(ChannelActivity.this).enqueue(request);
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