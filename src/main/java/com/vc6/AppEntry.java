package com.vc6;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.vc6.core.persistence.ConfigStore;
import com.vc6.gui.MainStage;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.net.Socket;

public class AppEntry extends Application {


    private static final int INSTANCE_PORT = 59999; // 实例唤醒监听端口
    private static ServerSocket instanceSocket;
    private static Stage globalStage; // 静态引用，用于被后台线程唤起

    @Override
    public void start(Stage primaryStage) {

        globalStage = primaryStage; // 存入静态引用
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

    private static boolean checkAndWakeExistingInstance() {
        try {
            // 尝试绑定本地 59999 端口
            instanceSocket = new ServerSocket(INSTANCE_PORT, 0, InetAddress.getByName("127.0.0.1"));

            // 绑定成功，启动监听线程，等待后来者的唤醒信号
            Thread listener = new Thread(() -> {
                while (!instanceSocket.isClosed()) {
                    try (Socket ignored = instanceSocket.accept()) {
                        // 只要有连接进来，说明有人想唤醒我
                        Platform.runLater(AppEntry::showWindow);
                    } catch (IOException e) {
                        break;
                    }
                }
            });
            listener.setDaemon(true);
            listener.start();
            return true; // 绑定成功，我是主实例

        } catch (IOException e) {
            // 绑定失败，说明已经有一个主实例了
            try (Socket socket = new Socket("127.0.0.1", INSTANCE_PORT)) {
                // 向主实例发个信号就走
                socket.getOutputStream().write(1);
            } catch (IOException ignored) {}
            return false; // 我是副实例，应该退出
        }
    }

    private static void showWindow() {
        if (globalStage != null) {
            if (globalStage.isIconified()) globalStage.setIconified(false); // 取消最小化
            globalStage.show();
            globalStage.toFront(); // 置顶显示
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
        config.sessionExpiryTimeProperty().addListener(o -> ConfigStore.save());
        config.sessionExpiryTimeProperty().addListener(o -> ConfigStore.save());
        config.localShareHistoryProperty().addListener(o -> ConfigStore.save());
        config.debugModeProperty().addListener(o -> ConfigStore.save());
        config.deviceNameProperty().addListener(o -> ConfigStore.save());
        config.quickShareExpireHoursProperty().addListener(o -> ConfigStore.save());
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
            com.vc6.core.service.DiscoveryService.getInstance().stop();
            if (instanceSocket != null) instanceSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Platform.exit();
        System.exit(0);
    }


    public static void main(String[] args) {

        if (!checkAndWakeExistingInstance()) {
            // 唤醒动作在 check 内部已经做了，这里直接退出自己
            System.out.println("检测到已有实例运行，已发送唤醒信号。");
            return;
        }
        launch(args);
    }
}