package com.vc6.core.persistence;

import com.vc6.model.AppConfig;
import java.io.*;
import java.util.Properties;

public class ConfigStore {

    private static final String CONFIG_FILE = "config.properties";

    public static void load() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            AppConfig config = AppConfig.getInstance();

            // 基础
            parse(props, "server.port", config::setPort, Integer::parseInt);
            parse(props, "local.root_path", config::setRootPath, s -> s);
            parse(props, "server.allow_upload", config::setAllowUpload, Boolean::parseBoolean);
            parse(props, "security.pin", config::setRemotePin, s -> s);
            parse(props, "security.global_auth", config::setGlobalAuthEnabled, Boolean::parseBoolean);
            parse(props, "quick.path", config::setQuickSharePath, s -> s);
            parse(props, "network.interface", config::setPreferredNetworkInterface, s -> s);
            parse(props, "system.minimize_tray", config::setMinimizeToTray, Boolean::parseBoolean);
            parse(props, "quick.max_file_mb", config::setMaxFileSizeMb, Long::parseLong);
            parse(props, "quick.max_text_len", config::setMaxTextLength, Integer::parseInt);
            parse(props, "ui.dark_mode", config::setDarkMode, Boolean::parseBoolean);
            parse(props, "ui.scale", config::setUiScalePercent, Integer::parseInt);
            parse(props, "security.session_expiry", config::setSessionExpiryDays, Integer::parseInt);
            parse(props, "local.history", config::setLocalShareHistory, s -> s);
            parse(props, "system.debug", config::setDebugMode, Boolean::parseBoolean);
            parse(props, "web.title", config::setWebTitle, s -> s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        AppConfig config = AppConfig.getInstance();
        Properties props = new Properties();

        props.setProperty("server.port", String.valueOf(config.getPort()));
        props.setProperty("local.root_path", config.getRootPath());
        props.setProperty("server.allow_upload", String.valueOf(config.isAllowUpload()));
        props.setProperty("security.pin", config.getRemotePin());
        props.setProperty("quick.path", config.getQuickSharePath());
        props.setProperty("network.interface", config.getPreferredNetworkInterface());
        props.setProperty("system.minimize_tray", String.valueOf(config.isMinimizeToTray()));
        props.setProperty("quick.max_file_mb", String.valueOf(config.getMaxFileSizeMb()));
        props.setProperty("quick.max_text_len", String.valueOf(config.getMaxTextLength()));
        props.setProperty("ui.dark_mode", String.valueOf(config.isDarkMode()));
        props.setProperty("ui.scale", String.valueOf(config.getUiScalePercent()));
        props.setProperty("security.global_auth", String.valueOf(config.isGlobalAuthEnabled()));
        props.setProperty("security.session_expiry", String.valueOf(config.getSessionExpiryDays()));
        props.setProperty("local.history", config.getLocalShareHistory() == null ? "" : config.getLocalShareHistory());
        props.setProperty("system.debug", String.valueOf(config.isDebugMode()));
        props.setProperty("web.title", config.getWebTitle());

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "LAN Linker Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 辅助解析方法，避免一堆 try-catch
    private static <T> void parse(Properties props, String key, java.util.function.Consumer<T> setter, java.util.function.Function<String, T> mapper) {
        String val = props.getProperty(key);
        if (val != null && !val.isEmpty()) {
            try { setter.accept(mapper.apply(val)); } catch (Exception ignored) {}
        }
    }
}