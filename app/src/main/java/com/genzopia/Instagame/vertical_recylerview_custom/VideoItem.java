package com.genzopia.Instagame.vertical_recylerview_custom;

import com.google.android.exoplayer2.ExoPlayer;

public class VideoItem {
    public String id;
    public String title;
    public String channelName;
    public String views;
    public String timeAgo;
    public String channelIconUrl;
    public String videoUrl;  // Add video URL
    public String description; // Add description field
    public String developerId; // Add developer ID for follow tracking
    public String gameId; // Add game ID for play button functionality
    public boolean isPlaying;
    public ExoPlayer preloadedPlayer; // Store preloaded player for thumbnail

    public VideoItem(String id, String title, String channelName,
                     String views, String timeAgo, String channelIconUrl, String videoUrl) {
        this.id = id;
        this.title = title;
        this.channelName = channelName;
        this.views = views;
        this.timeAgo = timeAgo;
        this.channelIconUrl = channelIconUrl;
        this.videoUrl = videoUrl;
        this.description = ""; // Default empty description
        this.developerId = ""; // Default empty developer ID
        this.gameId = ""; // Default empty game ID
    }
    
    public VideoItem(String id, String title, String channelName,
                     String views, String timeAgo, String channelIconUrl, String videoUrl, String description) {
        this.id = id;
        this.title = title;
        this.channelName = channelName;
        this.views = views;
        this.timeAgo = timeAgo;
        this.channelIconUrl = channelIconUrl;
        this.videoUrl = videoUrl;
        this.description = description;
        this.developerId = ""; // Default empty developer ID
        this.gameId = ""; // Default empty game ID
    }
    
    public VideoItem(String id, String title, String channelName,
                     String views, String timeAgo, String channelIconUrl, String videoUrl, String description, String developerId) {
        this.id = id;
        this.title = title;
        this.channelName = channelName;
        this.views = views;
        this.timeAgo = timeAgo;
        this.channelIconUrl = channelIconUrl;
        this.videoUrl = videoUrl;
        this.description = description;
        this.developerId = developerId;
        this.gameId = ""; // Default empty game ID
    }
    
    public VideoItem(String id, String title, String channelName,
                     String views, String timeAgo, String channelIconUrl, String videoUrl, String description, String developerId, String gameId) {
        this.id = id;
        this.title = title;
        this.channelName = channelName;
        this.views = views;
        this.timeAgo = timeAgo;
        this.channelIconUrl = channelIconUrl;
        this.videoUrl = videoUrl;
        this.description = description;
        this.developerId = developerId;
        this.gameId = gameId;
    }
}