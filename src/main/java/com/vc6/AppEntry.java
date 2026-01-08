package com.vc6;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.vc6.core.NettyServer;
import com.vc6.core.persistence.ConfigStore;
import com.vc6.gui.MainStage;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.net.URL;

public class AppEntry extends Application {

    private static final java.io.File LOCK_FILE = new java.io.File(System.getProperty("java.io.tmpdir"), "lanlinker_instance.lock");
    private static java.io.RandomAccessFile randomAccessFile;
    private static java.nio.channels.FileLock fileLock;
    @Override
    public void start(Stage primaryStage) {
        ConfigStore.load();

        boolean isDark = AppConfig.getInstance().isDarkMode();
        Application.setUserAgentStylesheet(isDark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());

        setupAutoSave();

        MainStage stage = new MainStage(primaryStage);

        stage.getScene().getStylesheets().add(
                getClass().getResource("/static/global.css").toExternalForm()
        );
        applyUiScale(stage.getScene().getRoot());



        primaryStage.setOnCloseRequest(e -> {
            // 1. 如果开启了托盘最小化 -> 隐藏窗口，不退出
            if (AppConfig.getInstance().isMinimizeToTray()) {
                e.consume(); // 阻止默认关闭
                primaryStage.hide();
            } else {
                // 2. 如果没开托盘 -> 彻底自杀
                stopApp();
            }
        });

        if (SystemTray.isSupported()) { // 稍微改一下判断条件，始终尝试加载托盘，只在点击X时判断行为
            setupSystemTray(primaryStage);
        }
    }

    /**
     * 【核心修复】注册所有属性的监听器
     * 只要这些值发生变化，立刻写入磁盘
     */
    private void setupAutoSave() {
        AppConfig config = AppConfig.getInstance();

        // 基础配置
        config.portProperty().addListener(o -> ConfigStore.save());
        config.rootPathProperty().addListener(o -> ConfigStore.save());
        config.allowUploadProperty().addListener(o -> ConfigStore.save());
        config.remotePinProperty().addListener(o -> ConfigStore.save());
        config.globalAuthEnabledProperty().addListener(o -> ConfigStore.save());
        config.quickSharePathProperty().addListener(o -> ConfigStore.save());
        // 新增配置 (必须加上这些，否则新设置无法保存)
        config.preferredNetworkInterfaceProperty().addListener(o -> ConfigStore.save());
        config.minimizeToTrayProperty().addListener(o -> ConfigStore.save());
        config.maxFileSizeMbProperty().addListener(o -> ConfigStore.save());
        config.maxTextLengthProperty().addListener(o -> ConfigStore.save());
        config.isDarkModeProperty().addListener(o -> ConfigStore.save());
        config.uiScalePercentProperty().addListener(o -> ConfigStore.save());
        config.sessionExpiryDaysProperty().addListener(o -> ConfigStore.save());
        config.sessionExpiryDaysProperty().addListener(o -> ConfigStore.save());
        config.localShareHistoryProperty().addListener(o -> ConfigStore.save());
        config.debugModeProperty().addListener(o -> ConfigStore.save());
        config.webTitleProperty().addListener(o -> ConfigStore.save());
    }

    private void applyUiScale(javafx.scene.Node root) {
        int scale = AppConfig.getInstance().getUiScalePercent();
        double fontSize = 14 * (scale / 100.0);

        root.setStyle("-fx-font-size: " + fontSize + "px;");
    }

    private void setupSystemTray(Stage stage) {
        if (!SystemTray.isSupported()) return;
        Platform.setImplicitExit(false);

        try {
            SystemTray tray = SystemTray.getSystemTray();
            URL imageUrl = getClass().getResource("/tray.png");
            if (imageUrl == null) return;
            Image image = ImageIO.read(imageUrl);

            PopupMenu popup = new PopupMenu();
            MenuItem statusItem = new MenuItem("Status: initializing...");
              statusItem.addActionListener(e -> Platform.runLater(() -> {
                    stage.show();
                    stage.toFront();
                }));
            // 绑定状态更新 (JavaFX 线程 -> AWT 线程)
            AppConfig.getInstance().serverModeProperty().addListener((obs, old, mode) -> {
                String text = (mode == ServerMode.STOPPED) ? "Not running" : "Running: " + mode.getEngDescription();
                // AWT 组件必须在 AWT 线程更新
                java.awt.EventQueue.invokeLater(() -> statusItem.setLabel(text));
            });
            // 初始设置
            statusItem.setLabel("Not running");

            // 2. 显示主界面
            MenuItem showItem = new MenuItem("Open Menu");
            showItem.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            // 3. 退出
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> stopApp());

            popup.add(statusItem);
            popup.addSeparator(); // 分隔线
            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(image, "LAN Linker", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            tray.add(trayIcon);

            // 手动触发一次状态刷新
            statusItem.setLabel(AppConfig.getInstance().getServerMode() == ServerMode.STOPPED ? "Not running" : "Running: " + AppConfig.getInstance().getServerMode().getEngDescription());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopApp() {
        try {
            com.vc6.core.NettyServer.getInstance().stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Platform.exit();
        System.exit(0);
    }

    private static boolean isAppRunning() {
        try {
            // 尝试锁定文件
            randomAccessFile = new java.io.RandomAccessFile(LOCK_FILE, "rw");
            fileLock = randomAccessFile.getChannel().tryLock();
            // 如果拿不到锁 (null)，说明被占用了
            return fileLock == null;
        } catch (Exception e) {
            return true; // 出错也视为已运行，保险起见
        }
    }
    public static void main(String[] args) {

        if (isAppRunning()) {
            // 弹窗提示 (使用 Swing，因为此时 JavaFX 还没启动)
            javax.swing.JOptionPane.showMessageDialog(null,
                    "程序已在运行中！\n请检查右下角托盘图标或任务管理器。",
                    "重复启动",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            System.exit(0);
            return;
        }
        launch(args);
    }
}