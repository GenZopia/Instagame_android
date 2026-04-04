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
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.channel_view.Fragment.DetailFragment.DetailsFragment;
import com.genzopia.Instagame.channel_view.Fragment.GamesFragment.GamesFragment;
import com.genzopia.Instagame.channel_view.Fragment.VideosFragment.VideosFragment;
import com.google.android.material.button.MaterialButton;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private static final String TAG = "profile_photo";

    private FirebaseAuth auth;
    private SharedPreferences sharedPreferences;
    private CircleImageView profileImage;
    private TextView usernameTop, bio, website;
    private TextView phoneText;
    private TextView postsCount, followersCount, followingCount;
    private MaterialButton editProfileBtn;
    private ImageView menuIcon, videos_ff, games_ff, details_ff;
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private GamesFragment gamesFragment;
    private VideosFragment videosFragment;
    private DetailsFragment detailsFragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        initializeViews(view);
        initializeFragments(view);
        setupClickListeners();
        fetchUserData();
        return view;
    }

    private void initializeViews(View view) {
        // Profile section
        profileImage = view.findViewById(R.id.profileImage);
        usernameTop = view.findViewById(R.id.usernameTop);
        bio = view.findViewById(R.id.bio);
        website = view.findViewById(R.id.website);


        // Stats section
        postsCount = view.findViewById(R.id.postsCount);
        followersCount = view.findViewById(R.id.followersCount);
        followingCount = view.findViewById(R.id.followingCount);
        
        // Buttons and icons
        editProfileBtn = view.findViewById(R.id.editProfileBtn);
        menuIcon = view.findViewById(R.id.menuIcon);
        

        // Initialize Firebase Auth and SharedPreferences
        auth = FirebaseAuth.getInstance();
        sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
    }

    private void initializeFragments(View view) {
        videos_ff = view.findViewById(R.id.videos_ff);
        games_ff = view.findViewById(R.id.games_ff);
        details_ff = view.findViewById(R.id.details_ff);

        gamesFragment = new GamesFragment();
        videosFragment = new VideosFragment();
        detailsFragment = new DetailsFragment();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        gamesFragment.setDeveloperId(userId);
        videosFragment.setDeveloperId(userId);
        detailsFragment.setDeveloperId(userId);

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .add(R.id.contentGridPlaceholder, gamesFragment)
                    .add(R.id.contentGridPlaceholder, videosFragment)
                    .add(R.id.contentGridPlaceholder, detailsFragment)
                    .hide(videosFragment)
                    .hide(detailsFragment)
                    .commit();

            videos_ff.setAlpha(0.5f);
            games_ff.setAlpha(0.5f);
            details_ff.setAlpha(0.5f);

            // Hide all fragments
            getActivity().getSupportFragmentManager().beginTransaction()
                    .hide(videosFragment)
                    .hide(gamesFragment)
                    .hide(detailsFragment)
                    .commit();

            getActivity().getSupportFragmentManager().beginTransaction()
                    .show(videosFragment)
                    .commit();

            videos_ff.setAlpha(1.0f);
        }
    }

    private void switchTab(String tab) {
        // Reset all tabs to inactive state
        videos_ff.setAlpha(0.5f);
        games_ff.setAlpha(0.5f);
        details_ff.setAlpha(0.5f);

        // Hide all fragments
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .hide(videosFragment)
                    .hide(gamesFragment)
                    .hide(detailsFragment)
                    .commit();

            // Activate selected tab and show corresponding fragment
            switch (tab) {
                case "Games":
                    games_ff.setAlpha(1.0f);
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .show(gamesFragment)
                            .commit();
                    break;
                case "Videos":
                    videos_ff.setAlpha(1.0f);
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .show(videosFragment)
                            .commit();
                    break;
                case "Details":
                    details_ff.setAlpha(1.0f);
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .show(detailsFragment)
                            .commit();
                    break;
            }
        }
    }

    private void setupClickListeners() {
        profileImage.setOnClickListener(v -> openFullScreenImage());
        
        editProfileBtn.setOnClickListener(v -> {
            // Open EditProfileActivity to allow editing full name, bio, website, and phone
            Intent intent = new Intent(getActivity(), EditProfileActivity.class);
            startActivity(intent);
        });

        menuIcon.setOnClickListener(v -> showProfileMenu());

        // Tab click listeners
        videos_ff.setOnClickListener(v -> switchTab("Videos"));
        games_ff.setOnClickListener(v -> switchTab("Games"));
        details_ff.setOnClickListener(v -> switchTab("Details"));
    }

    private void fetchUserData() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "fetchUserData: currentUser is null — aborting");
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d(TAG, "fetchUserData: userId = " + userId);

        userRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(userId);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: snapshot exists = " + dataSnapshot.exists());
                if (!dataSnapshot.exists() || !isAdded()) {
                    Log.e(TAG, "onDataChange: snapshot missing or fragment detached");
                    return;
                }

                // Profile photo
                String profilePhotoUrl = dataSnapshot.child("profile_photo_url").getValue(String.class);
                Log.d(TAG, "onDataChange: raw profile_photo_url = '" + profilePhotoUrl + "'");

                String sanitizedPhotoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(profilePhotoUrl);
                Log.d(TAG, "onDataChange: sanitized url = '" + sanitizedPhotoUrl + "'");

                if (sanitizedPhotoUrl != null && getActivity() != null) {
                    Log.d(TAG, "onDataChange: calling Glide.load() with url = " + sanitizedPhotoUrl);
                    Glide.with(ProfileFragment.this)
                            .load(sanitizedPhotoUrl)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                @Override
                                public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        boolean isFirstResource) {
                                    Log.e(TAG, "Glide.onLoadFailed: url=" + sanitizedPhotoUrl
                                            + " error=" + (e != null ? e.getMessage() : "null"));
                                    if (e != null) e.logRootCauses(TAG);
                                    return false;
                                }
                                @Override
                                public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                    Log.d(TAG, "Glide.onResourceReady: image loaded successfully from " + dataSource);
                                    return false;
                                }
                            })
                            .into(profileImage);
                    sharedPreferences.edit().putString("profilePhotoUrl", sanitizedPhotoUrl).apply();
                } else {
                    Log.e(TAG, "onDataChange: sanitizedPhotoUrl is null or activity is null — skipping Glide load");
                }

                // Username and full name
                String fullName = dataSnapshot.child("full_name").getValue(String.class);
                Log.d(TAG, "onDataChange: full_name = '" + fullName + "'");
                usernameTop.setText(fullName);

                // Bio and website
                String userBio = dataSnapshot.child("bio").getValue(String.class);
                String userWebsite = dataSnapshot.child("website").getValue(String.class);
                bio.setText(userBio != null ? userBio : "Add a bio to tell your story!");
                website.setText(userWebsite != null ? userWebsite : "Add your website");

                // Stats
                long posts = dataSnapshot.child("posts").getChildrenCount();
                String followers = dataSnapshot.child("followers").getValue(String.class);
                long following = dataSnapshot.child("following").getChildrenCount();

                postsCount.setText(String.valueOf(posts));
                followersCount.setText(followers != null ? followers : "0");
                followingCount.setText(String.valueOf(following));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "onCancelled: " + error.getMessage());
                Toast.makeText(getContext(), "Failed to fetch user data", Toast.LENGTH_SHORT).show();
            }
        };
        userRef.addValueEventListener(userListener);
    }

    private void openFullScreenImage() {
        Intent intent = new Intent(getActivity(), FullScreenImageActivity.class);
        String profilePhotoUrl = sharedPreferences.getString("profilePhotoUrl", "");
        intent.putExtra("image", profilePhotoUrl);
        startActivity(intent);
    }

    private void showProfileMenu() {
        // TODO: Implement menu UI and handle option selection
        logout(); // Temporary: directly calling logout for now
    }


    private void logout() {
        clearSharedPreferences();
        
        if (auth != null) {
            auth.signOut();
        }

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
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }
}
