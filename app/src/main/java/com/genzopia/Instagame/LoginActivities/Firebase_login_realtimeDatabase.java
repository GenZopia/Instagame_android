package com.genzopia.Instagame.LoginActivities;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.genzopia.Instagame.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Firebase_login_realtimeDatabase {
    private DatabaseReference databaseReference;
    private FirebaseAuth auth;

    public Firebase_login_realtimeDatabase() {
        databaseReference = FirebaseDatabase.getInstance().getReference();
        auth = FirebaseAuth.getInstance();
    }

    public void handleGoogleSignIn(GoogleSignInAccount account, Activity activity) {
        String userId = auth.getCurrentUser().getUid();
        
        // Check if user already exists
        databaseReference.child("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    // New user - create profile
                    User user = new User(
                        userId,
                        account.getEmail() != null ? account.getEmail() : "-1",
                        account.getDisplayName() != null ? account.getDisplayName() : "-1",
                        "-1", // Date of birth not available from Google
                        "-1"  // Mobile number not available from Google
                    );
                    
                    // Set profile photo if available, otherwise -1
                    user.setProfile_photo_url(account.getPhotoUrl() != null ? 
                        account.getPhotoUrl().toString() : "-1");

                    // Save user data
                    databaseReference.child("users").child(userId).setValue(user)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(activity, "Welcome to GenZopia!", Toast.LENGTH_SHORT).show();
                            goToMainActivity(activity);
                        })
                        .addOnFailureListener(e -> 
                            Toast.makeText(activity, "Failed to create profile", Toast.LENGTH_SHORT).show()
                        );
                } else {
                    // Existing user - just go to main activity
                    goToMainActivity(activity);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(activity, "Database error: " + databaseError.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goToMainActivity(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
