package com.vc6.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class RemoteDevice {
    private final StringProperty ip = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final int port;
    private long lastSeen; // 用于清理离线设备

    public RemoteDevice(String ip, int port, String name) {
        this.ip.set(ip);
        this.port = port;
        this.name.set(name);
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    // 【新增】获取上次活跃时间
    public long getLastSeen() {
        return lastSeen;
    }

    public String getUrl() {
        return "http://" + getIp() + ":" + port;
    }

    // Getters
    public String getIp() { return ip.get(); }
    public String getName() { return name.get(); }
    public int getPort() { return port; }
    public StringProperty nameProperty() { return name; }
    public StringProperty ipProperty() { return ip; }

    // 重写 equals/hashCode 方便去重
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteDevice that = (RemoteDevice) o;
        return port == that.port && getIp().equals(that.getIp());
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(getIp(), port); }
}