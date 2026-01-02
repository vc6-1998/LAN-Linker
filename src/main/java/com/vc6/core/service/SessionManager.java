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
    private static final SessionManager INSTANCE = new SessionManager();

    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final ObservableList<UserSession> sessionList = FXCollections.observableArrayList();
    private final Properties userStore = new Properties();
    private final File userFile = new File("users.properties");

    private SessionManager() {
        loadUsers();
    }

    public static SessionManager getInstance() { return INSTANCE; }

    private void loadUsers() {
        if (!userFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(userFile), StandardCharsets.UTF_8)) {
            userStore.load(reader);

            // 【核心修复】遍历配置文件，把老用户“复活”到内存里
            for (String key : userStore.stringPropertyNames()) {
                // 存储格式约定：uid.prop (例如 1234.name, 1234.ip)
                if (key.contains(".")) {
                    String uid = key.split("\\.")[0];
                    if (!sessions.containsKey(uid)) {
                        String name = userStore.getProperty(uid + ".name");
                        String ip = userStore.getProperty(uid + ".ip", "Unknown");
                        String dev = userStore.getProperty(uid + ".dev", "Unknown");

                        if (name != null) {
                            UserSession s = new UserSession(uid, ip, dev);
                            s.setNickname(name);
                            // 绑定监听
                            bindSaveListener(s);
                            // 复活进内存！
                            sessions.put(uid, s);
                            Platform.runLater(() -> sessionList.add(s));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveUsers() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(userFile), StandardCharsets.UTF_8)) {
            // 将内存里的所有会话状态写入文件
            // 格式：uid.name, uid.ip, uid.dev
            for (UserSession s : sessions.values()) {
                String uid = s.getUserId();
                userStore.setProperty(uid + ".name", s.getNickname());
                userStore.setProperty(uid + ".ip", s.getIp());
                userStore.setProperty(uid + ".dev", s.getDeviceName());
            }
            userStore.store(writer, "User Database");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void bindSaveListener(UserSession s) {
        // 任何属性变动都触发保存
        s.nicknameProperty().addListener(o -> saveUsers());
        s.ipProperty().addListener(o -> saveUsers()); // IP变了也更新
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
        if (sessions.containsKey(uid)) {
            UserSession s = sessions.get(uid);
            s.setIp(ip); // 更新最新 IP
            s.updateLastActive();
            return s;
        }

        // 创建新会话
        UserSession s = new UserSession(uid, ip, parseDevice(userAgent));
        bindSaveListener(s);

        sessions.put(uid, s);
        Platform.runLater(() -> sessionList.add(s));
        saveUsers(); // 立即保存新用户
        return s;
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