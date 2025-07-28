package com.genzopia.Instagame.ui.components;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.genzopia.Instagame.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoDetailsBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_VIDEO_ID = "video_id";
    private static final String ARG_VIDEO_TITLE = "video_title";
    private static final String ARG_VIDEO_DESCRIPTION = "video_description";

    private String videoId;
    private String videoTitle;
    private String videoDescription;
    private String fetchedVideoTitle; // Store the video title fetched from Firebase

    private TextView videoTitleText;
    private TextView viewCountText;
    private TextView likeCountText;
    private TextView shareCountText;
    private TextView videoDescriptionText;
    private TextView uploadDateText;
    private MaterialButton shareButton;
    private MaterialButton reportButton;
    private ImageView closeButton;

    private DatabaseReference videoRef;
    private ValueEventListener videoListener;

    public static VideoDetailsBottomSheet newInstance(String videoId, String videoTitle, String videoDescription) {
        VideoDetailsBottomSheet fragment = new VideoDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId);
        args.putString(ARG_VIDEO_TITLE, videoTitle);
        args.putString(ARG_VIDEO_DESCRIPTION, videoDescription);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            videoId = getArguments().getString(ARG_VIDEO_ID);
            videoTitle = getArguments().getString(ARG_VIDEO_TITLE);
            videoDescription = getArguments().getString(ARG_VIDEO_DESCRIPTION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_video_details, container, false);
        initializeViews(view);
        setupClickListeners();
        loadVideoDetails();
        return view;
    }

    private void initializeViews(View view) {
        videoTitleText = view.findViewById(R.id.videoTitle);
        viewCountText = view.findViewById(R.id.viewCount);
        likeCountText = view.findViewById(R.id.likeCount);
        shareCountText = view.findViewById(R.id.shareCount);
        videoDescriptionText = view.findViewById(R.id.videoDescription);
        uploadDateText = view.findViewById(R.id.uploadDate);
        shareButton = view.findViewById(R.id.shareButton);
        reportButton = view.findViewById(R.id.reportButton);
        closeButton = view.findViewById(R.id.closeButton);

        // Set initial data - title will be set from Firebase
        videoTitleText.setText("Loading...");
        videoDescriptionText.setText(videoDescription != null ? videoDescription : "No description available");
    }

    private void setupClickListeners() {
        shareButton.setOnClickListener(v -> {
            // TODO: Implement share functionality
            Toast.makeText(getContext(), "Share functionality coming soon!", Toast.LENGTH_SHORT).show();
        });

        reportButton.setOnClickListener(v -> {
            // TODO: Implement report functionality
            Toast.makeText(getContext(), "Report functionality coming soon!", Toast.LENGTH_SHORT).show();
        });

        closeButton.setOnClickListener(v -> dismiss());
    }

    private void loadVideoDetails() {
        if (videoId == null) {
            Toast.makeText(getContext(), "Video ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        videoRef = FirebaseDatabase.getInstance().getReference("videos").child(videoId);
        videoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Toast.makeText(getContext(), "Video not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Load view count
                String viewCount = dataSnapshot.child("view_count").getValue(String.class);
                if (viewCount != null) {
                    viewCountText.setText(formatCount(Long.parseLong(viewCount)));
                } else {
                    viewCountText.setText("0");
                }

                // Load like count
                String likeCount = dataSnapshot.child("like_count").getValue(String.class);
                if (likeCount != null) {
                    likeCountText.setText(formatCount(Long.parseLong(likeCount)));
                } else {
                    likeCountText.setText("0");
                }

                // Load share count
                String shareCount = dataSnapshot.child("share_count").getValue(String.class);
                if (shareCount != null) {
                    shareCountText.setText(formatCount(Long.parseLong(shareCount)));
                } else {
                    shareCountText.setText("0");
                }

                // Load upload date
                String createdAt = dataSnapshot.child("created_at").getValue(String.class);
                if (createdAt != null) {
                    uploadDateText.setText("Uploaded on " + formatDate(createdAt));
                } else {
                    uploadDateText.setText("Upload date unknown");
                }

                // Update video title and description if available
                String title = dataSnapshot.child("video_title").getValue(String.class);
                if (title != null && !title.isEmpty()) {
                    fetchedVideoTitle = title; // Store the fetched title
                }

                String description = dataSnapshot.child("description").getValue(String.class);
                if (description != null && !description.isEmpty()) {
                    videoDescriptionText.setText(description);
                }
                
                // Load game name from game_id
                String gameId = dataSnapshot.child("game_id").getValue(String.class);
                if (gameId != null && !gameId.isEmpty()) {
                    loadGameName(gameId);
                } else {
                    // If no game_id, try to get from gameid field (for backward compatibility)
                    gameId = dataSnapshot.child("gameid").getValue(String.class);
                    if (gameId != null && !gameId.isEmpty()) {
                        loadGameName(gameId);
                    } else {
                        // No game associated - show video title as fallback
                        if (fetchedVideoTitle != null && !fetchedVideoTitle.isEmpty()) {
                            videoTitleText.setText(fetchedVideoTitle);
                        } else {
                            videoTitleText.setText("Untitled Video");
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load video details", Toast.LENGTH_SHORT).show();
            }
        };
        videoRef.addValueEventListener(videoListener);
    }
    
    private void loadGameName(String gameId) {
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String gameName = snapshot.child("game_name").getValue(String.class);
                    if (gameName != null && !gameName.isEmpty()) {
                        // Get video title from the existing video data
                        String videoTitle = fetchedVideoTitle != null ? fetchedVideoTitle : "Untitled Video";
                        
                        // Create SpannableString with video title and colored @game_name
                        String fullText = videoTitle + " @" + gameName;
                        android.text.SpannableString spannableString = new android.text.SpannableString(fullText);
                        
                        // Find the position of @game_name
                        int gameNameStart = fullText.indexOf("@" + gameName);
                        if (gameNameStart != -1) {
                            // Apply theme color to @game_name
                            int themeColor = getResources().getColor(R.color.button_primary, null);
                            spannableString.setSpan(
                                new android.text.style.ForegroundColorSpan(themeColor),
                                gameNameStart,
                                gameNameStart + ("@" + gameName).length(),
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                        
                        videoTitleText.setText(spannableString);
                    } else {
                        // Fallback to video title if game name not found
                        String title = fetchedVideoTitle != null ? fetchedVideoTitle : "Untitled Video";
                        videoTitleText.setText(title);
                    }
                } else {
                    // Fallback to video title if game not found
                    String title = fetchedVideoTitle != null ? fetchedVideoTitle : "Untitled Video";
                    videoTitleText.setText(title);
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback to video title on error
                String title = fetchedVideoTitle != null ? fetchedVideoTitle : "Untitled Video";
                videoTitleText.setText(title);
            }
        });
    }

    private String formatCount(long count) {
        if (count < 1000) {
            return String.valueOf(count);
        } else if (count < 1000000) {
            return String.format("%.1fK", count / 1000.0);
        } else {
            return String.format("%.1fM", count / 1000000.0);
        }
    }

    private String formatDate(String dateString) {
        try {
            // Parse the date format: "2025-07-28-05-04-50"
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            
            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            // If parsing fails, return the original string
            return dateString;
        }
        return dateString;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (videoRef != null && videoListener != null) {
            videoRef.removeEventListener(videoListener);
        }
    }
} 