package com.genzopia.Instagame.channel_view.Fragment.DetailFragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinkAdapter adapter;
    private List<LinkItem> linkList;
    private String developerId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.linksRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize link list
        linkList = new ArrayList<>();

        adapter = new LinkAdapter(linkList);
        recyclerView.setAdapter(adapter);
        
        // Load developer details if developer ID is set
        if (developerId != null) {
            loadDeveloperDetails();
        }
    }
    
    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
        if (isAdded() && recyclerView != null) {
            loadDeveloperDetails();
        }
    }
    
    private void loadDeveloperDetails() {
        if (developerId == null) {
            Log.e("DetailsFragment", "Developer ID is null");
            return;
        }
        
        Log.d("DetailsFragment", "Loading details for developer: " + developerId);
        
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(developerId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                linkList.clear();
                
                if (snapshot.exists()) {
                    // Add developer details as link items
                    String email = snapshot.child("email").getValue(String.class);
                    String mobileNo = snapshot.child("mobile_no").getValue(String.class);
                    String fullName = snapshot.child("full_name").getValue(String.class);
                    String followers = snapshot.child("followers").getValue(String.class);
                    String dateOfBirth = snapshot.child("date_of_birth").getValue(String.class);
                    
                    // Add basic info
                    if (fullName != null && !fullName.isEmpty()) {
                        linkList.add(new LinkItem("Full Name", fullName));
                    }
                    
                    if (email != null && !email.isEmpty()) {
                        linkList.add(new LinkItem("Email", email));
                    }
                    
                    if (mobileNo != null && !mobileNo.isEmpty()) {
                        linkList.add(new LinkItem("Mobile", mobileNo));
                    }
                    
                    if (followers != null && !followers.isEmpty()) {
                        linkList.add(new LinkItem("Followers", followers));
                    }
                    
                    if (dateOfBirth != null && !dateOfBirth.isEmpty()) {
                        linkList.add(new LinkItem("Date of Birth", dateOfBirth));
                    }
                    
                    // Count videos and games
                    DataSnapshot videosSnapshot = snapshot.child("videos");
                    DataSnapshot gamesSnapshot = snapshot.child("games");
                    
                    int videoCount = 0;
                    int gameCount = 0;
                    
                    if (videosSnapshot.exists()) {
                        videoCount = (int) videosSnapshot.getChildrenCount();
                    }
                    
                    if (gamesSnapshot.exists()) {
                        gameCount = (int) gamesSnapshot.getChildrenCount();
                    }
                    
                    linkList.add(new LinkItem("Total Videos", String.valueOf(videoCount)));
                    linkList.add(new LinkItem("Total Games", String.valueOf(gameCount)));
                    
                    Log.d("DetailsFragment", "Loaded details for developer: " + fullName+dateOfBirth);
                } else {
                    Log.d("DetailsFragment", "Developer not found: " + developerId);
                    linkList.add(new LinkItem("Error", "Developer not found"));
                }
                
                adapter.notifyDataSetChanged();
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("DetailsFragment", "Error loading developer details: " + error.getMessage());
                linkList.clear();
                linkList.add(new LinkItem("Error", "Failed to load details"));
                adapter.notifyDataSetChanged();
            }
        });
    }
}
