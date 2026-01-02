package com.vc6.model;

import javafx.beans.property.*;

public class UserSession {
    private final String userId;
    private final StringProperty ip = new SimpleStringProperty(); // 必须是 SimpleStringProperty
    private final StringProperty deviceName = new SimpleStringProperty();
    private final StringProperty nickname = new SimpleStringProperty();
    private final LongProperty lastActive = new SimpleLongProperty();
    private final DoubleProperty currentUploadProgress = new SimpleDoubleProperty(-1);

    public UserSession(String userId, String ip, String deviceName) {
        this.userId = userId;
        this.ip.set(ip);
        this.deviceName.set(deviceName);
        this.nickname.set(this.getDeviceName() +"_" + userId.substring(0, 4));
        this.lastActive.set(System.currentTimeMillis());
    }

    // --- IP 属性的方法 ---
    public String getIp() { return ip.get(); }
    public void setIp(String value) { this.ip.set(value); }
    public StringProperty ipProperty() { return ip; }

    // --- UserId (只读) ---
    public String getUserId() { return userId; }

    // --- Nickname 属性的方法 ---
    public String getNickname() { return nickname.get(); }
    public void setNickname(String value) { this.nickname.set(value); }
    public StringProperty nicknameProperty() { return nickname; }

    // --- DeviceName 属性的方法 ---
    public String getDeviceName() { return deviceName.get(); }
    public void setDeviceName(String value) { this.deviceName.set(value); }
    public StringProperty deviceNameProperty() { return deviceName; }

    // --- LastActive 属性的方法 ---
    public long getLastActive() { return lastActive.get(); }
    public void updateLastActive() { this.lastActive.set(System.currentTimeMillis()); }
    public LongProperty lastActiveProperty() { return lastActive; }

    // --- UploadProgress 属性的方法 ---
    public double getCurrentUploadProgress() { return currentUploadProgress.get(); }
    public void setCurrentUploadProgress(double value) { this.currentUploadProgress.set(value); }
    public DoubleProperty currentUploadProgressProperty() { return currentUploadProgress; }
}