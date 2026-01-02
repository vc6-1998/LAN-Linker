package com.vc6.model;

public enum ServerMode {
    STOPPED("已停止", false,"Stopped"),
    LOCAL_SHARE("本地目录共享", true,"Local Share"),
    QUICK_SHARE("极速快传", true,"Quick Share"),
    REMOTE_DISK("远程全盘访问", true,"Remote ALL Disk"),;

    private final String description;
    private final boolean isRunning;
    private final String engDescription;

    ServerMode(String description, boolean isRunning, String engDescription) {
        this.description = description;
        this.isRunning = isRunning;
        this.engDescription = engDescription;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRunning() {
        return isRunning;
    }
    public String getEngDescription() {
        return engDescription;
    }
}
