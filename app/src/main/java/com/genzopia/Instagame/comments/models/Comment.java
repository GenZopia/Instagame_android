package com.genzopia.Instagame.comments.models;

public class Comment {
    public String comment_id;
    public String user_id;
    public String user_display_name;
    public String user_photo_url;
    public String text;
    public Long created_at;
    public Long like_count;
    public Long dislike_count;
    public Long reply_count;

    public Comment() {}
}


