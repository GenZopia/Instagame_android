package com.genzopia.Instagame.LoginActivities;

public class User {
    private String user_id;
    private String date_of_birth;
    private String email;
    private String full_name;
    private String mobile_no;
    private String profile_photo_url;
    private String profile_photo_id;
    private String followers;

    // Empty constructor required for Firebase
    public User() {
    }

    public User(String user_id, String email, String full_name, String date_of_birth, String mobile_no) {
        this.user_id = user_id;
        this.date_of_birth = date_of_birth;
        this.email = email;
        this.full_name = full_name;
        this.mobile_no = mobile_no;
        this.profile_photo_url = ""; // Will be updated after uploading to Firebase Storage
        this.followers = "0"; // Initially 0 followers
    }

    // Getters and Setters
    public String getuser_id() {
        return user_id;
    }

    public void setuser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getDate_of_birth() {
        return date_of_birth;
    }

    public void setDate_of_birth(String date_of_birth) {
        this.date_of_birth = date_of_birth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }

    public String getMobile_no() {
        return mobile_no;
    }

    public void setMobile_no(String mobile_no) {
        this.mobile_no = mobile_no;
    }

    public String getProfile_photo_url() {
        return profile_photo_url;
    }

    public void setProfile_photo_url(String profile_photo_url) {
        this.profile_photo_url = profile_photo_url;
    }

    public String getProfile_photo_id() {
        return profile_photo_id;
    }

    public void setProfile_photo_id(String profile_photo_id) {
        this.profile_photo_id = profile_photo_id;
    }

    public String getFollowers() {
        return followers;
    }

    public void setFollowers(String followers) {
        this.followers = followers;
    }
}
