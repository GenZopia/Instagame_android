package com.genzopia.Instagame.reelview;

import java.util.ArrayList;

// ReelItem class (if you want to keep it here)
public class ReelItem {
    private final String videoId;
    private final String title;
    private final String likeCount;
    private final String description;
    private final String developerId;
    private final String gameid;

    public ReelItem(ArrayList<String> data) {
        this.videoId = data.get(0);
        this.title = data.get(1);
        this.likeCount = data.get(2);
        this.description = data.get(3);
        this.developerId = data.get(4);
        this.gameid = data.get(5);
    }

    public String getVideoId() { return videoId; }
    public String getTitle() { return title; }
    public String getLikeCount() { return likeCount; }
    public String getDescription() { return description; }
    public String getDeveloperId() { return developerId; }
    public String getGameId() { return gameid; }
}