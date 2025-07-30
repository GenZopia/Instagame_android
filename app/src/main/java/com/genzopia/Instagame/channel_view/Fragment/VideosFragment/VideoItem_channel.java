package com.genzopia.Instagame.channel_view.Fragment.VideosFragment;

public class VideoItem_channel {
    private String videoId;
    private String thumbnailUrl;
    private String views;
    private String videoTitle;
    private Boolean isVerified;
    private boolean isOwnChannel;

    public VideoItem_channel() {}

    public VideoItem_channel(String videoId, String thumbnailUrl, String views) {
        this.videoId = videoId;
        this.thumbnailUrl = thumbnailUrl;
        this.views = views;
        this.videoTitle = "";
        this.isVerified = false;
        this.isOwnChannel = false;
    }

    public VideoItem_channel(String videoId, String thumbnailUrl, String views, String videoTitle, Boolean isVerified, boolean isOwnChannel) {
        this.videoId = videoId;
        this.thumbnailUrl = thumbnailUrl;
        this.views = views;
        this.videoTitle = videoTitle != null ? videoTitle : "";
        this.isVerified = isVerified != null ? isVerified : false;
        this.isOwnChannel = isOwnChannel;
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

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public boolean isOwnChannel() {
        return isOwnChannel;
    }

    public void setOwnChannel(boolean ownChannel) {
        isOwnChannel = ownChannel;
    }
}
