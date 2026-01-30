package com.vc6.model;

import javafx.beans.property.*;

public class AppConfig {
    private static final AppConfig INSTANCE = new AppConfig();

    // --- 核心服务 ---
    private final IntegerProperty port = new SimpleIntegerProperty(8080);
    private final StringProperty rootPath = new SimpleStringProperty(System.getProperty("user.home"));
    private final BooleanProperty allowUpload = new SimpleBooleanProperty(true);
    private final ObjectProperty<ServerMode> serverMode = new SimpleObjectProperty<>(ServerMode.STOPPED);
    private final StringProperty deviceName = new SimpleStringProperty(com.vc6.utils.IpUtils.getHostName());

    // --- 网络与安全 ---
    private final StringProperty preferredNetworkInterface = new SimpleStringProperty("Auto"); // 优先网卡
    private final StringProperty remotePin = new SimpleStringProperty("123456");
    private final BooleanProperty globalAuthEnabled = new SimpleBooleanProperty(false); // 默认关闭
    private final IntegerProperty sessionExpiryTime = new SimpleIntegerProperty(1);
    // --- 系统集成 ---
    private final BooleanProperty minimizeToTray = new SimpleBooleanProperty(true); // 最小化到托盘


    // --- 快传限制 ---
    private final StringProperty quickSharePath = new SimpleStringProperty(
            new java.io.File(System.getProperty("user.dir"), "quick_share").getAbsolutePath()
    );
    private final LongProperty maxFileSizeMb = new SimpleLongProperty(1024);
    private final IntegerProperty maxTextLength = new SimpleIntegerProperty(32767);
    private final IntegerProperty quickShareExpireHours = new SimpleIntegerProperty(1);


    // --- 外观 ---.
    private final BooleanProperty isDarkMode = new SimpleBooleanProperty(false); // 默认深色
    private final IntegerProperty uiScalePercent = new SimpleIntegerProperty(120); // 缩放比例

    private final StringProperty localShareHistory = new SimpleStringProperty("");

    private final BooleanProperty debugMode = new SimpleBooleanProperty(false); // 默认关闭

    private final BooleanProperty discoveryEnabled = new SimpleBooleanProperty(true); // 默认开启


    private AppConfig() {
        // 自动创建快传目录
        quickSharePath.addListener((obs, old, newVal) -> new java.io.File(newVal).mkdirs());
        // 初始创建
        new java.io.File(getQuickSharePath()).mkdirs();
    }

    public static AppConfig getInstance() { return INSTANCE; }

    // --- Getters, Setters, Properties ---

    public int getPort() { return port.get(); }
    public void setPort(int port) { this.port.set(port); }
    public IntegerProperty portProperty() { return port; }

    public String getRootPath() { return rootPath.get(); }
    public void setRootPath(String path) { this.rootPath.set(path); }
    public StringProperty rootPathProperty() { return rootPath; }

    public boolean isAllowUpload() { return allowUpload.get(); }
    public void setAllowUpload(boolean allow) { this.allowUpload.set(allow); }
    public BooleanProperty allowUploadProperty() { return allowUpload; }

    public ServerMode getServerMode() { return serverMode.get(); }
    public void setServerMode(ServerMode mode) { this.serverMode.set(mode); }
    public ObjectProperty<ServerMode> serverModeProperty() { return serverMode; }

    public String getPreferredNetworkInterface() { return preferredNetworkInterface.get(); }
    public void setPreferredNetworkInterface(String ip) { this.preferredNetworkInterface.set(ip); }
    public StringProperty preferredNetworkInterfaceProperty() { return preferredNetworkInterface; }

    public String getRemotePin() { return remotePin.get(); }
    public void setRemotePin(String pin) { this.remotePin.set(pin); }
    public StringProperty remotePinProperty() { return remotePin; }

    public boolean isMinimizeToTray() { return minimizeToTray.get(); }
    public void setMinimizeToTray(boolean val) { this.minimizeToTray.set(val); }
    public BooleanProperty minimizeToTrayProperty() { return minimizeToTray; }

    public String getQuickSharePath() { return quickSharePath.get(); }
    public void setQuickSharePath(String path) { this.quickSharePath.set(path); }
    public StringProperty quickSharePathProperty() { return quickSharePath; }


    public long getMaxFileSizeMb() { return maxFileSizeMb.get(); }
    public void setMaxFileSizeMb(long mb) { this.maxFileSizeMb.set(mb); }
    public LongProperty maxFileSizeMbProperty() { return maxFileSizeMb; }

    public int getMaxTextLength() { return maxTextLength.get(); }
    public void setMaxTextLength(int len) { this.maxTextLength.set(len); }
    public IntegerProperty maxTextLengthProperty() { return maxTextLength; }

    public boolean isDarkMode() { return isDarkMode.get(); }
    public void setDarkMode(boolean val) { this.isDarkMode.set(val); }
    public BooleanProperty isDarkModeProperty() { return isDarkMode; }

    public int getUiScalePercent() { return uiScalePercent.get(); }
    public void setUiScalePercent(int val) { this.uiScalePercent.set(val); }
    public IntegerProperty uiScalePercentProperty() { return uiScalePercent; }

    public boolean isGlobalAuthEnabled() { return globalAuthEnabled.get(); }
    public void setGlobalAuthEnabled(boolean val) { this.globalAuthEnabled.set(val); }
    public BooleanProperty globalAuthEnabledProperty() { return globalAuthEnabled; }

    public int getSessionExpiryTime() { return sessionExpiryTime.get(); }
    public void setSessionExpiryTime(int val) { this.sessionExpiryTime.set(val); }
    public IntegerProperty sessionExpiryTimeProperty() { return sessionExpiryTime; }

    public String getLocalShareHistory() { return localShareHistory.get(); }
    public void setLocalShareHistory(String val) { this.localShareHistory.set(val); }
    public StringProperty localShareHistoryProperty() { return localShareHistory; }

    public boolean isDebugMode() { return debugMode.get(); }
    public void setDebugMode(boolean val) { this.debugMode.set(val); }
    public BooleanProperty debugModeProperty() { return debugMode; }

    public String getdeviceName() { return deviceName.get(); }
    public void setdeviceName(String title) { this.deviceName.set(title); }
    public StringProperty deviceNameProperty() { return deviceName; }

    public boolean isDiscoveryEnabled() { return discoveryEnabled.get(); }
    public void setDiscoveryEnabled(boolean val) { this.discoveryEnabled.set(val); }
    public BooleanProperty discoveryEnabledProperty() { return discoveryEnabled; }

    public int getQuickShareExpireHours() { return quickShareExpireHours.get(); }
    public void setQuickShareExpireHours(int hours) { this.quickShareExpireHours.set(hours); }
    public IntegerProperty quickShareExpireHoursProperty() { return quickShareExpireHours; }

}