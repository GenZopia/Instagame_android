package com.genzopia.Instagame.profile_recyclerview;

public class ImageItem {
    private String id;
    private String imageUrl;

    public ImageItem(String id, String imageUrl) {
        this.id = id;
        this.imageUrl = imageUrl;
    }

    public String getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}

