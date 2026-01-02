package com.vc6.utils;

import com.vc6.model.AppConfig;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MessageUtils {

    // 保存主窗口引用，用于定位 Toast
    private static Stage primaryStage;

    public static void init(Stage stage) {
        primaryStage = stage;
    }

    /**
     * 显示普通信息弹窗 (需要点击确定)
     */
    public static void showInfo(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null); // 去掉默认的头部，更简洁
            alert.setContentText(content);
            alert.initOwner(primaryStage); // 设为模态，必须关掉才能操作主窗口
            alert.showAndWait();
        });
    }

    /**
     * 显示错误弹窗
     */
    public static void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.initOwner(primaryStage);
            alert.showAndWait();
        });
    }

    /**
     * 【核心新功能】显示闪现消息 (Toast)
     * 2秒后自动消失，不打断用户操作
     */
    /**
     * 显示闪现消息 (自动适配主题反色)
     */
    public static void showToast(String message) {
        if (primaryStage == null) return;

        Platform.runLater(() -> {
            Popup popup = new Popup();
            Label label = new Label(message);

            // 1. 获取当前主题状态
            boolean isDark = AppConfig.getInstance().isDarkMode();

            // 2. 设置高对比度颜色 (深色主题用白底黑字，浅色主题用黑底白字)
            String styleBase = "-fx-padding: 10 20; -fx-background-radius: 20; -fx-font-size: 14px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 0);";

            if (isDark) {
                // 深色模式 -> 亮白色气泡
                label.setStyle(styleBase + "-fx-background-color: rgba(255, 255, 255, 0.95); -fx-text-fill: #333;");
            } else {
                // 浅色模式 -> 深黑色气泡
                label.setStyle(styleBase + "-fx-background-color: rgba(40, 40, 40, 0.9); -fx-text-fill: white;");
            }

            popup.getContent().add(label);
            popup.setAutoHide(true);

            popup.show(primaryStage);

            // 居中计算
            double x = primaryStage.getX() + (primaryStage.getWidth() - label.getWidth()) / 2;
            double y = primaryStage.getY() + primaryStage.getHeight() - 100;
            popup.setX(x);
            popup.setY(y);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(500), label);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> popup.hide());

            PauseTransition delay = new PauseTransition(Duration.millis(2000));
            delay.setOnFinished(e -> fadeOut.play());
            delay.play();
        });
    }
    public static boolean showConfirm(String title, String content) {
        // 创建一个在 UI 线程执行的任务
        if (Platform.isFxApplicationThread()) {
            // 如果已经在 UI 线程，直接弹窗，不用 FutureTask (防止死锁)
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.initOwner(primaryStage);
            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK;
        }
        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<>(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.initOwner(primaryStage);

            // 等待用户点击
            java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK;
        });

        // 提交给 UI 线程
        Platform.runLater(task);

        try {
            // 后台线程在这里阻塞，直到用户点完按钮
            return task.get();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}