package com.genzopia.Instagame.reelview;

public class ReelItem {
    private String videoId;
    private String title;
    private String likeCount;
    private String description;
    private String developerId;
    private String gameid;
    private String videoUrl; // New field for signed URL
    private int videoDuration; // Duration in seconds

    public ReelItem(String videoId, String title, String likeCount, String description, String developerId, String gameid) {
        this.videoId = videoId != null ? videoId : "";
        this.title = title != null ? title : "Untitled Video";
        this.likeCount = likeCount != null ? likeCount : "0";
        this.description = description != null ? description : "";
        this.developerId = developerId != null ? developerId : "";
        this.gameid = gameid != null ? gameid : "";
        this.videoUrl = null;
        this.videoDuration = 30; // Default duration
    }

    // Getters
    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public String getLikeCount() {
        return likeCount;
    }

    public String getDescription() {
        return description;
    }

    public String getDeveloperId() {
        return developerId;
    }

    public String getGameid() {
        return gameid;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public int getVideoDuration() {
        return videoDuration;
    }

    // Setters
    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setLikeCount(String likeCount) {
        this.likeCount = likeCount;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDeveloperId(String developerId) {
        this.developerId = developerId;
    }

    public void setGameid(String gameid) {
        this.gameid = gameid;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public void setVideoDuration(int videoDuration) {
        this.videoDuration = videoDuration;
    }
}
