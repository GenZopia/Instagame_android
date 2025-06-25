package com.genzopia.Instagame.ui.profile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import com.genzopia.Instagame.FullScreenImageActivity;
import com.genzopia.Instagame.R;

import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileFragment extends Fragment {

    private FirebaseAuth auth;
    private SharedPreferences sharedPreferences;
    private Button logoutButton;
    private CircleImageView circularButton;
    private TextView userName;

    @SuppressLint("ClickableViewAccessibility")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        circularButton = view.findViewById(R.id.circularButton);
        logoutButton = view.findViewById(R.id.logout_btn);
        userName = view.findViewById(R.id.userName);

        logoutButton.setOnClickListener(v -> logout());

        fetchUserProfilePhoto();
        fetchUserName();

        circularButton.setOnClickListener(v ->
                Toast.makeText(getActivity(), "Circular button clicked!", Toast.LENGTH_SHORT).show()
        );

        circularButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(1.25f).scaleY(1.25f).setDuration(200).start();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                    Intent intent = new Intent(getActivity(), FullScreenImageActivity.class);
                    SharedPreferences sharedPref = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
                    String profilePhotoUrl = sharedPref.getString("profilePhotoUrl", "");
                    intent.putExtra("image", profilePhotoUrl);
                    startActivity(intent);
                    return true;
                default:
                    return false;
            }
        });

        return view;
    }

    private void fetchUserProfilePhoto() {
        sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
        String email = sharedPreferences.getString("email", "");

        if (email != null && !email.isEmpty()) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(email.replace(".", ","));
            userRef.get().addOnSuccessListener(dataSnapshot -> {
                if (dataSnapshot.exists()) {
                    String profilePhotoUrl = dataSnapshot.child("profilePhotoUrl").getValue(String.class);
                    if (isAdded() && getActivity() != null) {
                        Glide.with(this).load(profilePhotoUrl)
                                .error(R.drawable.profile).into(circularButton);
                    }
                    sharedPreferences.edit().putString("profilePhotoUrl", profilePhotoUrl).apply();
                } else if (isAdded() && getActivity() != null) {
                    Glide.with(this).load(R.drawable.profile).into(circularButton);
                }
            }).addOnFailureListener(e ->
                    Toast.makeText(getActivity(), "Failed to fetch user data", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void fetchUserName() {
        sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
        String email = sharedPreferences.getString("email", "");

        if (email != null && !email.isEmpty()) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference()
                    .child("users").child(email.replace(".", ","));
            userRef.get().addOnSuccessListener(dataSnapshot -> {
                if (dataSnapshot.exists()) {
                    String fullName = dataSnapshot.child("fullName").getValue(String.class);
                    userName.setText(fullName);
                    sharedPreferences.edit().putString("fullName", fullName).apply();
                } else {
                    Toast.makeText(getActivity(), "User data not found", Toast.LENGTH_SHORT).show();
                }
            }).addOnFailureListener(e ->
                    Toast.makeText(getActivity(), "Failed to fetch user data", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void logout() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        SharedPreferences sharedPrefs = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
        String gmail = sharedPrefs.getString("email", "");
        String encodedGmail = gmail != null ? gmail.replace(".", ",") : "";
        DatabaseReference myRef = database.getReference("users").child(encodedGmail).child("app_online_status");
        myRef.setValue(false);

        clearSharedPreferences();

        auth = FirebaseAuth.getInstance();
        auth.signOut();

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void clearSharedPreferences() {
        sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();
    }
}
