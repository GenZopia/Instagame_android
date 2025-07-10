package com.genzopia.Instagame.ui.profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

    private FirebaseAuth auth;
    private SharedPreferences sharedPreferences;
    private CircleImageView profileImage;
    private TextView usernameTop, bio, website;
    private TextView postsCount, followersCount, followingCount;
    private MaterialButton editProfileBtn;
    private ImageView menuIcon, tabPosts, tabReels, tabTagged;
    private DatabaseReference userRef;
    private ValueEventListener userListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        initializeViews(view);
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
        
        // Tabs
        tabPosts = view.findViewById(R.id.tabPosts);
        tabReels = view.findViewById(R.id.tabReels);
        tabTagged = view.findViewById(R.id.tabTagged);

        // Initialize Firebase Auth and SharedPreferences
        auth = FirebaseAuth.getInstance();
        sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
    }

    private void setupClickListeners() {
        profileImage.setOnClickListener(v -> openFullScreenImage());
        
        editProfileBtn.setOnClickListener(v -> {
            // TODO: Implement edit profile functionality
            Toast.makeText(getContext(), "Edit Profile coming soon!", Toast.LENGTH_SHORT).show();
        });

        menuIcon.setOnClickListener(v -> showProfileMenu());

        // Tab click listeners
        tabPosts.setOnClickListener(v -> switchTab("posts"));
        tabReels.setOnClickListener(v -> switchTab("reels"));
        tabTagged.setOnClickListener(v -> switchTab("tagged"));
    }

    private void fetchUserData() {
        String email = sharedPreferences.getString("email", "");
        if (email == null || email.isEmpty()) return;

        userRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(email.replace(".", ","));

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists() || !isAdded()) return;

                // Profile photo
                String profilePhotoUrl = dataSnapshot.child("profilePhotoUrl").getValue(String.class);
                if (profilePhotoUrl != null && getActivity() != null) {
                    Glide.with(ProfileFragment.this)
                            .load(profilePhotoUrl)
                            .error(R.drawable.profile)
                            .into(profileImage);
                    sharedPreferences.edit().putString("profilePhotoUrl", profilePhotoUrl).apply();
                }

                // Username and full name
                String fullName = dataSnapshot.child("fullName").getValue(String.class);
                String username = dataSnapshot.child("username").getValue(String.class);
                usernameTop.setText(username != null ? username : fullName);

                // Bio and website
                String userBio = dataSnapshot.child("bio").getValue(String.class);
                String userWebsite = dataSnapshot.child("website").getValue(String.class);
                bio.setText(userBio != null ? userBio : "Add a bio to tell your story!");
                website.setText(userWebsite != null ? userWebsite : "Add your website");

                // Stats
                long posts = dataSnapshot.child("posts").getChildrenCount();
                long followers = dataSnapshot.child("followers").getChildrenCount();
                long following = dataSnapshot.child("following").getChildrenCount();

                postsCount.setText(String.valueOf(posts));
                followersCount.setText(String.valueOf(followers));
                followingCount.setText(String.valueOf(following));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
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
        // Show a bottom sheet or popup menu with options
        String[] options = {"Settings", "Archive", "Your Activity", "QR Code", "Saved", "Close Friends", "Logout"};
        // TODO: Implement menu UI and handle option selection
        logout(); // Temporary: directly calling logout for now
    }

    private void switchTab(String tab) {
        // Reset all tabs to inactive state
        tabPosts.setAlpha(0.5f);
        tabReels.setAlpha(0.5f);
        tabTagged.setAlpha(0.5f);

        // Activate selected tab
        switch (tab) {
            case "posts":
                tabPosts.setAlpha(1.0f);
                // TODO: Show posts grid
                break;
            case "reels":
                tabReels.setAlpha(1.0f);
                // TODO: Show reels grid
                break;
            case "tagged":
                tabTagged.setAlpha(1.0f);
                // TODO: Show tagged posts grid
                break;
        }
    }

    private void logout() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        String gmail = sharedPreferences.getString("email", "");
        String encodedGmail = gmail != null ? gmail.replace(".", ",") : "";
        DatabaseReference myRef = database.getReference("users").child(encodedGmail).child("app_online_status");
        myRef.setValue(false);

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
