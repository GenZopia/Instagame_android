package com.genzopia.Instagame.LoginActivities;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.MainActivity;
import com.google.firebase.database.FirebaseDatabase;

public class Firebase_login_realtimeDatabase extends AppCompatActivity {
    private String sharedPrefFile = "LoginPrefs";
    private FirebaseDatabase database;
// In Firebase_login_realtimeDatabase.java

    public void create_user(String emailAddress, String fullName, String profilePhotoUrl,
                            String dob, String mobileNumber, boolean app_online_status, int coin, Context con) {
        database = FirebaseDatabase.getInstance();
        User firebaseUser = new User(emailAddress, fullName, profilePhotoUrl, dob, mobileNumber, app_online_status, coin);

        database.getReference("users")
                .child(emailAddress.replace(".", ","))
                .setValue(firebaseUser)
                .addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        con.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                                .putString("email", emailAddress)
                                .apply();
                        con.startActivity(new Intent(con, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    } else {
                        Toast.makeText(con, "User creation failed: " + task1.getException().getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void create_user_register(String emailAddress, String fullName, String profilePhotoUrl,
                            String dob, String mobileNumber, boolean app_online_status, int coin, Context con) {
        database = FirebaseDatabase.getInstance();
        User firebaseUser = new User(emailAddress, fullName, profilePhotoUrl, dob, mobileNumber, app_online_status, coin);

        database.getReference("users")
                .child(emailAddress.replace(".", ","))
                .setValue(firebaseUser)
                .addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        con.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                                .putString("email", emailAddress)
                                .apply();
                        con.startActivity(new Intent(con, LoginActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    } else {
                        Toast.makeText(con, "User creation failed: " + task1.getException().getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


}
