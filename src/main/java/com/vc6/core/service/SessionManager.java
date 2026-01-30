package com.vc6.core.service;

import com.vc6.model.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final ObservableList<UserSession> sessionList = FXCollections.observableArrayList(
            user -> new javafx.beans.Observable[]{ user.valuableProperty() }
    );
    private final Properties userStore = new Properties();
    private final File userFile = new File("users.properties");


    private static final SessionManager INSTANCE = new SessionManager();


    private SessionManager() {
        loadUsers();
    }

    public static SessionManager getInstance() { return INSTANCE; }

    private void loadUsers() {
        if (!userFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(userFile), StandardCharsets.UTF_8)) {
            userStore.load(reader);
            for (String key : userStore.stringPropertyNames()) {
                if (key.contains(".")) {
                    String uid = key.split("\\.")[0];
                    if (!sessions.containsKey(uid)) {
                        String name = userStore.getProperty(uid + ".name");
                        String ip = userStore.getProperty(uid + ".ip", "Unknown");
                        String dev = userStore.getProperty(uid + ".dev", "Unknown");
                        String timeStr = userStore.getProperty(uid + ".time"); // 读取时间

                        if (name != null) {
                            UserSession s = new UserSession(uid, ip, dev);
                            s.setNickname(name);
                            s.setValuable(true);
                            // 恢复时间
                            if (timeStr != null) {
                                try { s.setLastActive(Long.parseLong(timeStr)); } catch(Exception e){}
                            }

                            bindSaveListener(s);
                            sessions.put(uid, s);
                            Platform.runLater(() -> sessionList.add(s));
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(userFile), StandardCharsets.UTF_8))) {
            writer.println("# User Database");
            for (UserSession s : sessions.values()) {
                if(!s.isValuable())
                    continue;
                String uid = s.getUserId();
                userStore.setProperty(uid + ".name", s.getNickname());
                userStore.setProperty(uid + ".ip", s.getIp());
                userStore.setProperty(uid + ".dev", s.getDeviceName());
                userStore.setProperty(uid + ".time", String.valueOf(s.getLastActive())); // 保存时间
            }
            for (String key : userStore.stringPropertyNames()) {
                writer.println(key + "=" + userStore.getProperty(key));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void bindSaveListener(UserSession s) {
        // 任何属性变动都触发保存
        s.nicknameProperty().addListener(o -> saveUsers());
        s.ipProperty().addListener(o -> saveUsers()); // IP变了也更新
        s.lastActiveProperty().addListener(o -> saveUsers()); // 监听时间变化
    }

    public UserSession getSession(String uid) {
        return sessions.get(uid);
    }

    public UserSession findSessionByIp(String ip) {
        for (UserSession s : sessions.values()) {
            if (s.getIp().equals(ip)) return s;
        }
        return null;
    }

    public UserSession getOrCreateSession(String uid, String ip, String userAgent) {
        // 1. 如果内存里已经有这个活着的会话了，直接返回
        if (sessions.containsKey(uid)) {
            UserSession s = sessions.get(uid);
            s.setIp(ip);
            s.updateLastActive();
            return s;
        }
        // 2. 内存里没有，创建一个新的对象
        UserSession s = new UserSession(uid, ip, parseDevice(userAgent));
        bindSaveListener(s);
        // 3. 【核心修复】去硬盘（userStore）里查，看他是不是以前登录成功的“老用户”
        String savedNick = userStore.getProperty(uid + ".name");
        if (savedNick != null) {
            s.setNickname(savedNick);
            s.setValuable(true); // 自动恢复登录态
        } else {
            s.setValuable(false); // 标记为未登录/临时访客
        }

        sessions.put(uid, s);
        Platform.runLater(() -> sessionList.add(s));
        return s;
    }
    public void removeSession(UserSession s) {
        // 1. 撤销“有价值”标记
        s.setValuable(false);
        String uid = s.getUserId();
        userStore.remove(uid + ".name");
        userStore.remove(uid + ".ip");
        userStore.remove(uid + ".dev");
        userStore.remove(uid + ".time");
        saveUsers();
    }


    private String parseDevice(String ua) {
        if (ua == null) return "未知设备";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Macintosh")) return "Mac";
        return "浏览器";
    }

    public ObservableList<UserSession> getSessionList() { return sessionList; }

}