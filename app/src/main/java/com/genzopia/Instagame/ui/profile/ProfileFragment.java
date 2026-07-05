package com.genzopia.Instagame.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.channel_view.Fragment.DetailFragment.DetailsFragment;
import com.genzopia.Instagame.channel_view.Fragment.GamesFragment.GamesFragment;
import com.genzopia.Instagame.channel_view.Fragment.VideosFragment.VideosFragment;
import com.google.android.material.button.MaterialButton;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;


import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private static final String TAG = "profile_photo";
    
    // Static cache to persist across fragment recreation by Navigation Component
    private static boolean sIsDataLoaded = false;
    private static String sCachedProfilePhotoUrl;
    private static String sCachedFullName;
    private static String sCachedBio;
    private static String sCachedWebsite;
    private static String sCachedStory;
    private static String sCachedPostsCount;
    private static String sCachedFollowersCount;
    private static String sCachedFollowingCount;

    private FirebaseAuth auth;
    private SharedPreferences sharedPreferences;
    private CircleImageView profileImage;
    private TextView usernameTop, bio, bioExpand, website, story;
    private TextView phoneText;
    private TextView postsCount, followersCount, followingCount;
    private MaterialButton editProfileBtn, genzLabBtn;
    private ImageView menuIcon, videos_ff, games_ff, details_ff;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Apply status-bar inset as top padding so content starts below the status bar
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                v.getPaddingLeft(),
                systemBars.top,
                v.getPaddingRight(),
                v.getPaddingBottom()
            );
            return insets;
        });

        initializeViews(view);
        initializeFragments(view);
        setupClickListeners();
        
        // Restore cached data immediately to avoid flickering
        if (sIsDataLoaded) {
            restoreCachedData();
        } else {
            fetchUserData();
            sIsDataLoaded = true;
        }
        
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfileScreenViewed();
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("profile");
        return view;
    }

    private void initializeViews(View view) {
        // Profile section
        profileImage = view.findViewById(R.id.profileImage);
        usernameTop = view.findViewById(R.id.usernameTop);
        bio = view.findViewById(R.id.bio);
        bioExpand = view.findViewById(R.id.bioExpand);
        website = view.findViewById(R.id.website);
        story = view.findViewById(R.id.story);


        // Stats section
        postsCount = view.findViewById(R.id.postsCount);
        followersCount = view.findViewById(R.id.followersCount);
        followingCount = view.findViewById(R.id.followingCount);
        
        // Buttons and icons
        editProfileBtn = view.findViewById(R.id.editProfileBtn);
        genzLabBtn = view.findViewById(R.id.genzLabBtn);
        menuIcon = view.findViewById(R.id.menuIcon);
        

        // Initialize Firebase Auth and SharedPreferences
        auth = FirebaseAuth.getInstance();
        sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
    }

    private void initializeFragments(View view) {
        videos_ff = view.findViewById(R.id.videos_ff);
        games_ff = view.findViewById(R.id.games_ff);
        details_ff = view.findViewById(R.id.details_ff);

        // Check if fragments are already added (restored by child fragment manager)
        GamesFragment gamesFragment = (GamesFragment) getChildFragmentManager().findFragmentByTag("games_fragment");
        VideosFragment videosFragment = (VideosFragment) getChildFragmentManager().findFragmentByTag("videos_fragment");
        DetailsFragment detailsFragment = (DetailsFragment) getChildFragmentManager().findFragmentByTag("details_fragment");

        // Only create and add fragments if they don't exist
        if (gamesFragment == null || videosFragment == null || detailsFragment == null) {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Create new fragments if needed
            if (gamesFragment == null) {
                gamesFragment = new GamesFragment();
                gamesFragment.setDeveloperId(userId);
            }
            if (videosFragment == null) {
                videosFragment = new VideosFragment();
                videosFragment.setDeveloperId(userId);
            }
            if (detailsFragment == null) {
                detailsFragment = new DetailsFragment();
                detailsFragment.setDeveloperId(userId);
            }

            // Add fragments to child fragment manager with tags
            getChildFragmentManager().beginTransaction()
                    .add(R.id.contentGridPlaceholder, gamesFragment, "games_fragment")
                    .add(R.id.contentGridPlaceholder, videosFragment, "videos_fragment")
                    .add(R.id.contentGridPlaceholder, detailsFragment, "details_fragment")
                    .hide(gamesFragment)
                    .hide(detailsFragment)
                    .commit();
        }

        // Reset tab states
        videos_ff.setAlpha(0.5f);
        games_ff.setAlpha(0.5f);
        details_ff.setAlpha(0.5f);

        // Show videos tab by default
        getChildFragmentManager().beginTransaction()
                .hide(gamesFragment)
                .hide(detailsFragment)
                .show(videosFragment)
                .commit();

        videos_ff.setAlpha(1.0f);
    }

    private void switchTab(String tab) {
        // Reset all tabs to inactive state
        videos_ff.setAlpha(0.5f);
        games_ff.setAlpha(0.5f);
        details_ff.setAlpha(0.5f);

        // Get fragment references from child fragment manager
        GamesFragment gamesFragment = (GamesFragment) getChildFragmentManager().findFragmentByTag("games_fragment");
        VideosFragment videosFragment = (VideosFragment) getChildFragmentManager().findFragmentByTag("videos_fragment");
        DetailsFragment detailsFragment = (DetailsFragment) getChildFragmentManager().findFragmentByTag("details_fragment");

        if (gamesFragment == null || videosFragment == null || detailsFragment == null) return;

        // Hide all fragments and show the selected one
        switch (tab) {
            case "Games":
                games_ff.setAlpha(1.0f);
                getChildFragmentManager().beginTransaction()
                        .hide(videosFragment)
                        .hide(detailsFragment)
                        .show(gamesFragment)
                        .commit();
                break;
            case "Videos":
                videos_ff.setAlpha(1.0f);
                getChildFragmentManager().beginTransaction()
                        .hide(gamesFragment)
                        .hide(detailsFragment)
                        .show(videosFragment)
                        .commit();
                break;
            case "Details":
                details_ff.setAlpha(1.0f);
                getChildFragmentManager().beginTransaction()
                        .hide(gamesFragment)
                        .hide(videosFragment)
                        .show(detailsFragment)
                        .commit();
                break;
        }
    }

    private void setupClickListeners() {
        profileImage.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfilePhotoTapped();
            openFullScreenImage();
        });
        
        editProfileBtn.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackEditProfileOpened();
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
        });

        website.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
        });

        story.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
        });

        bioExpand.setOnClickListener(v -> {
            if (bio.getMaxLines() == 3) {
                bio.setMaxLines(Integer.MAX_VALUE);
                bio.setEllipsize(null);
                bioExpand.setText("less");
            } else {
                bio.setMaxLines(3);
                bio.setEllipsize(android.text.TextUtils.TruncateAt.END);
                bioExpand.setText("more");
            }
        });

        menuIcon.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfileMenuOpened();
            showProfileMenu();
        });

        genzLabBtn.setOnClickListener(v ->
                Toast.makeText(getContext(), "Coming soon!", Toast.LENGTH_SHORT).show());

        // Tab click listeners
        videos_ff.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfileTabSwitched("videos");
            switchTab("Videos");
        });
        games_ff.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfileTabSwitched("games");
            switchTab("Games");
        });
        details_ff.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfileTabSwitched("details");
            switchTab("Details");
        });
    }

    private void fetchUserData() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "fetchUserData: currentUser is null — aborting");
            return;
        }

        com.genzopia.Instagame.gateway.GatewayClient.INSTANCE.getCallApi()
                .getMyProfile()
                .enqueue(new retrofit2.Callback<com.genzopia.Instagame.gateway.UserProfileDTO>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<com.genzopia.Instagame.gateway.UserProfileDTO> call,
                                           @NonNull retrofit2.Response<com.genzopia.Instagame.gateway.UserProfileDTO> resp) {
                        if (!resp.isSuccessful() || resp.body() == null || !isAdded()) return;
                        com.genzopia.Instagame.gateway.UserProfileDTO p = resp.body();

                        String sanitizedPhotoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(p.getProfile_photo_url());
                        sCachedProfilePhotoUrl = sanitizedPhotoUrl;
                        if (sanitizedPhotoUrl != null && getActivity() != null) {
                            com.genzopia.Instagame.glide.GlideImageLoader.load(
                                requireContext(), sanitizedPhotoUrl,
                                R.drawable.profile, profileImage);
                        }

                        sCachedFullName = p.getFull_name();
                        usernameTop.setText(sCachedFullName);

                        sCachedBio = p.getBio();
                        sCachedWebsite = p.getWebsite();
                        sCachedStory = p.getStory();

                        bio.setText(sCachedBio != null && !sCachedBio.isEmpty() ? sCachedBio : "Add a bio to tell your story!");
                        bio.setMaxLines(3);
                        bio.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        bio.post(() -> {
                            if (bio.getLayout() != null && bio.getLayout().getLineCount() > 3) {
                                bioExpand.setVisibility(View.VISIBLE);
                                bioExpand.setText("more");
                            } else {
                                bioExpand.setVisibility(View.GONE);
                            }
                        });
                        website.setText(sCachedWebsite != null && !sCachedWebsite.isEmpty() ? sCachedWebsite : "Add your website");
                        story.setText(sCachedStory != null && !sCachedStory.isEmpty() ? sCachedStory : "Add your story");

                        sCachedFollowersCount = String.valueOf(p.getFollowers_count());
                        followersCount.setText(sCachedFollowersCount);
                        sCachedPostsCount = "0";
                        sCachedFollowingCount = "0";
                        postsCount.setText(sCachedPostsCount);
                        followingCount.setText(sCachedFollowingCount);
                    }
                    @Override
                    public void onFailure(@NonNull retrofit2.Call<com.genzopia.Instagame.gateway.UserProfileDTO> call,
                                          @NonNull Throwable t) {
                        Log.e(TAG, "fetchUserData failed: " + t.getMessage());
                        if (isAdded())
                            Toast.makeText(getContext(), "Failed to fetch user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void restoreCachedData() {
        // Restore profile photo
        if (sCachedProfilePhotoUrl != null && getActivity() != null) {
            com.genzopia.Instagame.glide.GlideImageLoader.load(
                requireContext(), sCachedProfilePhotoUrl,
                R.drawable.profile, profileImage);
        }

        // Restore text data
        if (sCachedFullName != null) {
            usernameTop.setText(sCachedFullName);
        }
        
        bio.setText(sCachedBio != null ? sCachedBio : "Add a bio to tell your story!");
        bio.setMaxLines(3);
        bio.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bio.post(() -> {
            if (bio.getLayout() != null && bio.getLayout().getLineCount() > 3) {
                bioExpand.setVisibility(View.VISIBLE);
                bioExpand.setText("more");
            } else {
                bioExpand.setVisibility(View.GONE);
            }
        });
        
        website.setText(sCachedWebsite != null ? sCachedWebsite : "Add your website");
        story.setText(sCachedStory != null ? sCachedStory : "Add your story");

        // Restore stats
        if (sCachedPostsCount != null) {
            postsCount.setText(sCachedPostsCount);
        }
        if (sCachedFollowersCount != null) {
            followersCount.setText(sCachedFollowersCount);
        }
        if (sCachedFollowingCount != null) {
            followingCount.setText(sCachedFollowingCount);
        }
    }

    private void openFullScreenImage() {
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackProfilePhotoFullscreenOpened();
        Intent intent = new Intent(getActivity(), FullScreenImageActivity.class);
        intent.putExtra("image", sCachedProfilePhotoUrl);
        startActivity(intent);
    }

    private void showProfileMenu() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
        android.view.View v = getLayoutInflater().inflate(R.layout.bottom_sheet_profile_menu, null);
        v.findViewById(R.id.menuLogout).setOnClickListener(x -> { sheet.dismiss(); logout(); });
        sheet.setContentView(v);
        sheet.show();
    }


    private void logout() {
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLogoutTapped();
        long sessionDurationMs = com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.getSessionDurationMs();
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLogoutCompleted(sessionDurationMs);
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.clearIdentity();
        clearSharedPreferences();
        com.genzopia.Instagame.glide.GlideImageLoader.clearToken();
        if (auth != null) auth.signOut();

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void clearSharedPreferences() {
        if (sharedPreferences != null) {
            sharedPreferences.edit().clear().apply();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // No need to remove listener since we're using addListenerForSingleValueEvent
    }
}
