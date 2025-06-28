package com.genzopia.Instagame.channel_view.Fragment.VideosFragment;

public class VideoItem_channel {
    private String videoId;
    private String thumbnailUrl;
    private String views;

    public VideoItem_channel() {}

    public VideoItem_channel(String videoId, String thumbnailUrl, String views) {
        this.videoId = videoId;
        this.thumbnailUrl = thumbnailUrl;
        this.views = views;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getViews() {
        return views;
    }

    public void setViews(String views) {
        this.views = views;
    }
}
