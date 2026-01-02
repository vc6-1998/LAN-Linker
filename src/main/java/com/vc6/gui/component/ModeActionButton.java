package com.vc6.gui.component;

import atlantafx.base.theme.Styles;
import com.vc6.core.NettyServer;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import com.vc6.utils.IpUtils;
import com.vc6.utils.MessageUtils;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;

import java.io.File;

/**
 * 智能控制组：包含状态文字 + 圆形图标按钮
 */
public class ModeActionButton extends HBox {

    private final ServerMode targetMode;
    private final Button iconBtn;
    private final Label statusLabel;

    // SVG 图标路径数据 (来自 FontAwesome/Material Design)
    private static final String ICON_PLAY = "M8 5v14l11-7z";
    private static final String ICON_STOP = "M6 6h12v12H6z";
    private static final String ICON_SWAP = "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z";

    public ModeActionButton(ServerMode targetMode) {
        this.targetMode = targetMode;

        // 1. 初始化布局
        this.setAlignment(Pos.CENTER_RIGHT);
        this.setSpacing(15); // 文字和按钮的间距

        // 2. 状态文字
        statusLabel = new Label("初始化中...");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED); // 默认灰色

        // 3. 圆形图标按钮
        iconBtn = new Button();
        iconBtn.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.SUCCESS); // 默认绿色圆形
        iconBtn.setPrefSize(50, 50); // 大一点，好点
        iconBtn.setOnAction(e -> handleClick());

        // 4. 组装
        this.getChildren().addAll(statusLabel, iconBtn);

        // 5. 绑定全局状态监听
        AppConfig.getInstance().serverModeProperty().addListener((obs, oldMode, newMode) -> {
            updateState(newMode);
        });

        // 初始化状态
        updateState(AppConfig.getInstance().getServerMode());
    }

    private void updateState(ServerMode currentMode) {
        Platform.runLater(() -> {
            // 清除旧样式类 (虽然我们要用 setStyle，但还是清理一下好)
            iconBtn.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER, Styles.WARNING);
            iconBtn.setDisable(false);

            // 【关键修改】使用 setStyle 强制设置背景色
            // 这里的颜色变量 (-color-*-emphasis) 是 AtlantaFX 标准变量

            if (currentMode == ServerMode.STOPPED) {
                // --- 绿灯 ---
                statusLabel.setText("准备就绪，点击启动");
                statusLabel.setStyle("");

                // 强制绿色背景
                iconBtn.setStyle("-fx-background-color: -color-success-emphasis;");
                setIcon(ICON_PLAY);
                iconBtn.setTooltip(new Tooltip("启动 " + targetMode.getDescription()));

            } else if (currentMode == targetMode) {
                // --- 红灯 ---
                statusLabel.setText("正在运行中");
                statusLabel.setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");

                // 强制红色背景
                iconBtn.setStyle("-fx-background-color: -color-danger-emphasis;");
                setIcon(ICON_STOP);
                iconBtn.setTooltip(new Tooltip("停止服务"));

            } else {
                // --- 橙灯 (修复这里) ---
                statusLabel.setText(currentMode.getDescription() + " 运行中");
                statusLabel.setStyle("-fx-text-fill: -color-warning-fg;");

                // 【核心修复】强制橙色背景
                iconBtn.setStyle("-fx-background-color: -color-warning-emphasis;");
                setIcon(ICON_SWAP);
                iconBtn.setTooltip(new Tooltip("切换到 " + targetMode.getDescription()));
            }
        });
    }

    /**
     * 设置按钮图标 (使用 SVG)
     */
    private void setIcon(String svgContent) {
        SVGPath path = new SVGPath();
        path.setContent(svgContent);
        path.setStyle("-fx-fill: white;"); // 图标永远是白色

        // 稍微放大一点图标
        double scale = 1.5;
        path.setScaleX(scale);
        path.setScaleY(scale);

        iconBtn.setGraphic(path);
    }

    private void handleClick() {
        ServerMode currentMode = AppConfig.getInstance().getServerMode();
        int port = AppConfig.getInstance().getPort();
        iconBtn.setDisable(true); // 防抖
        new Thread(() -> {
            if (currentMode == targetMode) {
                NettyServer.getInstance().stop();
                MessageUtils.showToast("已停止 "+targetMode.getDescription());
            } else {
                if(!sendConfirm(targetMode)) {
                    Platform.runLater(() -> updateState(currentMode));
                    return;
                }
                if (!checkPreConditions()) {
                    // 检查不通过，恢复按钮
                    Platform.runLater(() -> updateState(currentMode));
                    return;
                }
                NettyServer.getInstance().start(targetMode);
            }
        }).start();
    }
    private boolean sendConfirm(ServerMode mode)
    {
        if(mode == ServerMode.QUICK_SHARE)
            return MessageUtils.showConfirm("启动确认","确定要启动 极速快传 模式吗？\n这将在局域网内公开列表中的内容。");
        else if(mode == ServerMode.LOCAL_SHARE)
            return MessageUtils.showConfirm("启动确认","确定要启动 本地目录共享 模式吗？\n这将在局域网内公开 "+AppConfig.getInstance().getRootPath()+" 下的所有内容。");
        else if(mode == ServerMode.REMOTE_DISK)
            return MessageUtils.showConfirm("启动确认","确定要启动 远程全盘访问 模式吗？\n这将在局域网内公开你电脑的 全部 文件！");
        return false;
    }
    private boolean checkPreConditions() {
        if (targetMode == ServerMode.LOCAL_SHARE) {
            String path = AppConfig.getInstance().getRootPath();
            // 防止空路径
            if (path == null || path.trim().isEmpty()) {
                MessageUtils.showError("启动出错","共享目录未设置！");
                return false;
            }

            File root = new File(path);
            if (!root.exists() || !root.isDirectory()) {
                MessageUtils.showError("启动出错","无效的共享目录：\n" + path);
                return false;
            }
        }
        if (targetMode == ServerMode.REMOTE_DISK) {
            String pin = AppConfig.getInstance().getRemotePin();
            boolean isGlobalAuth = AppConfig.getInstance().isGlobalAuthEnabled();

            if (pin == null || pin.trim().isEmpty()) {
                MessageUtils.showError("拒绝启动",
                        "为了您的隐私安全，请先在 [系统设置] 中\n设置访问 PIN 码。");
                return false;
            }

            if (!isGlobalAuth) {
                MessageUtils.showError("拒绝启动",
                        "为了您的隐私安全，请先在 [系统设置] 中\n开启 [全局网页访问保护]。");
                return false;
            }
        }

        if (AppConfig.getInstance().getServerMode() == ServerMode.STOPPED) {
            int port = AppConfig.getInstance().getPort();
            if (!IpUtils.isPortAvailable(port)) {
                MessageUtils.showError("启动出错","端口 " + port + " 被占用！\n请在设置中更改端口。");
                return false;
            }
        }
        return true;
    }
}