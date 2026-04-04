package com.genzopia.Instagame.channel_view.Fragment.DetailFragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DetailsFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinkAdapter adapter;
    private List<LinkItem> linkList;
    private String developerId;

    private TextView tvBio, tvFollowersCount, tvVideosCount, tvGamesCount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvBio            = view.findViewById(R.id.tvBio);
        tvFollowersCount = view.findViewById(R.id.tvFollowersCount);
        tvVideosCount    = view.findViewById(R.id.tvVideosCount);
        tvGamesCount     = view.findViewById(R.id.tvGamesCount);

        recyclerView = view.findViewById(R.id.linksRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        linkList = new ArrayList<>();
        adapter = new LinkAdapter(linkList);
        recyclerView.setAdapter(adapter);

        if (developerId != null) loadDeveloperDetails();
    }

    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
        if (isAdded() && recyclerView != null) loadDeveloperDetails();
    }

    private void loadDeveloperDetails() {
        if (developerId == null) return;

        FirebaseDatabase.getInstance().getReference("users").child(developerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        // Bio
                        String bio = snapshot.child("bio").getValue(String.class);
                        if (bio == null || bio.isEmpty())
                            bio = snapshot.child("description").getValue(String.class);
                        if (tvBio != null)
                            tvBio.setText(bio != null && !bio.isEmpty() ? bio : "No bio yet.");

                        // Stats
                        Long followersCount = snapshot.child("followers_count").getValue(Long.class);
                        if (followersCount == null) {
                            String fs = snapshot.child("followers").getValue(String.class);
                            try { followersCount = fs != null ? Long.parseLong(fs) : 0L; }
                            catch (NumberFormatException e) { followersCount = 0L; }
                        }
                        int videoCount = snapshot.child("videos").exists()
                                ? (int) snapshot.child("videos").getChildrenCount() : 0;
                        int gameCount = snapshot.child("games").exists()
                                ? (int) snapshot.child("games").getChildrenCount() : 0;

                        if (tvFollowersCount != null) tvFollowersCount.setText(formatCount(followersCount.intValue()));
                        if (tvVideosCount != null) tvVideosCount.setText(String.valueOf(videoCount));
                        if (tvGamesCount != null) tvGamesCount.setText(String.valueOf(gameCount));

                        // Info rows
                        linkList.clear();
                        String email = snapshot.child("email").getValue(String.class);
                        String username = snapshot.child("username").getValue(String.class);
                        String dob = snapshot.child("date_of_birth").getValue(String.class);
                        String website = snapshot.child("website").getValue(String.class);
                        String location = snapshot.child("location").getValue(String.class);

                        if (username != null && !username.isEmpty())
                            linkList.add(new LinkItem("Username", "@" + username));
                        if (email != null && !email.isEmpty())
                            linkList.add(new LinkItem("Email", email));
                        if (dob != null && !dob.isEmpty())
                            linkList.add(new LinkItem("Joined", dob));
                        if (website != null && !website.isEmpty())
                            linkList.add(new LinkItem("Website", website));
                        if (location != null && !location.isEmpty())
                            linkList.add(new LinkItem("Location", location));

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private String formatCount(int count) {
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }
}
