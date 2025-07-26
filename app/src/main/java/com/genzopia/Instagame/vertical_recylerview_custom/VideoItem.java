package com.genzopia.Instagame.vertical_recylerview_custom;

public class VideoItem {
    public String id;
    public String title;
    public String channelName;
    public String views;
    public String timeAgo;
    public String channelIconUrl;
    public String videoUrl;  // Add video URL
    public boolean isPlaying;

    public VideoItem(String id, String title, String channelName,
                     String views, String timeAgo, String channelIconUrl, String videoUrl) {
        this.id = id;
        this.title = title;
        this.channelName = channelName;
        this.views = views;
        this.timeAgo = timeAgo;
        this.channelIconUrl = channelIconUrl;
        this.videoUrl = videoUrl;
    }
}