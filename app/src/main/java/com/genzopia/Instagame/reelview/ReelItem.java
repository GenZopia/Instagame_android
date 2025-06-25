package com.genzopia.Instagame.reelview;

import java.util.ArrayList;

public class ReelItem {
    private String videoUrl;
    private String title;
    private String likeCount;
    private String description;
    private String developerId;
    private String secret;

    public ReelItem(ArrayList<String> data) {
        this.videoUrl = data.get(0);
        this.title = data.get(1);
        this.likeCount = data.get(2);
        this.description = data.get(3);
        this.developerId = data.get(4);
        this.secret = data.get(5);
    }

    // Getters
    public String getVideoUrl() { return videoUrl; }
    public String getTitle() { return title; }
    public String getLikeCount() { return likeCount; }
    public String getDescription() { return description; }
    public String getDeveloperId() { return developerId; }
    public String getSecret() { return secret; }
}
