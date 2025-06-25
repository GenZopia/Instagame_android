package com.genzopia.Instagame.LoginActivities;

public class User {
    private String emailAddress;  // Changed to emailAddress
    private String fullName;
    private String profilePhotoUrl;
    private String dob;
    private String mobileNumber;
    private boolean app_online_status; // Changed to app_online_status

    private long coin; // Changed to coin

    public User() {
    }

    public User(String emailAddress, String fullName, String profilePhotoUrl, String dob, String mobileNumber, boolean app_online_status, long coin) {
        this.emailAddress = emailAddress; // Changed from email
        this.fullName = fullName;
        this.profilePhotoUrl = profilePhotoUrl;
        this.dob = dob;
        this.mobileNumber = mobileNumber;
        this.app_online_status = app_online_status; // Changed from appOnlineStatus

        this.coin = coin; // Changed from coin
    }

    // Getters and setters for all fields
    public String getEmailAddress() { // Changed from getEmail()
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) { // Changed from setEmail()
        this.emailAddress = emailAddress;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public boolean isApp_online_status() { // Changed to isApp_online_status()
        return app_online_status;
    }

    public void setApp_online_status(boolean app_online_status) { // Changed to setApp_online_status()
        this.app_online_status = app_online_status;
    }

    public long getcoin() { // Changed from getcoin()
        return coin;
    }

    public void setcoin(long coin) { // Changed from setcoin()
        this.coin = coin;
    }
}
