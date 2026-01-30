package com.vc6.gui.component;

import atlantafx.base.theme.Styles;
import com.vc6.gui.MainStage;
import com.vc6.gui.view.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
// import org.kordamp.ikonli.javafx.FontIcon; // 如果有引入图标库

public class Sidebar {
    private final VBox view;
    private final MainStage mainStage;

    // 【核心修改 1】把所有页面提升为成员变量，只创建一次
    private final DashboardView dashboardView;
    private final SessionsView sessionsView;
    private final RadarView radarView;
    private final QuickShareView quickShareView;
    private final LocalShareView localShareView;
    private final RemoteDiskView remoteDiskView;
    private final LogView logView;
    private final SettingsView settingsView;


    private Button activeBtn;

    public Sidebar(MainStage mainStage) {
        this.mainStage = mainStage;
        this.view = new VBox(10);

        // 【核心修改 2】在构造函数里一次性初始化所有页面
        // 这样它们的状态就会一直保存在内存里，直到程序关闭
        this.dashboardView = new DashboardView();
        this.sessionsView = new SessionsView();
        this.radarView = new RadarView();
        this.quickShareView = new QuickShareView();
        this.localShareView = new LocalShareView();
        this.remoteDiskView = new RemoteDiskView();
        this.logView = new LogView();
        this.settingsView = new SettingsView();

        initView();
    }

    private void initView() {
        view.setPadding(new Insets(20));
        view.setPrefWidth(240);
        view.setStyle("-fx-background-color: -color-bg-subtle;");

        // Header
        Label logo = new Label("LAN Linker");
        logo.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        logo.setStyle("-fx-text-fill: -color-accent-fg;");

        // --- 导航区 1: 总览 ---
        Label group1 = createGroupLabel("控制台");
        Button btnDash = createBtn("仪表盘");
        btnDash.setOnAction(e -> {
            mainStage.switchView(dashboardView.getView());
            dashboardView.refresh();
            activate(btnDash);
        });
        Button btnUser = createBtn("访问设备记录");
        btnUser.setOnAction(e -> {
            mainStage.switchView(sessionsView.getView());
            activate(btnUser);
        });
        Button btnRadar = createBtn("连接其它服务");
        // 【核心修改 3】点击时直接使用已经缓存好的 view
        btnRadar.setOnAction(e -> {
            mainStage.switchView(radarView.getView());
            activate(btnRadar);
        });

        // --- 导航区 2: 功能模式 ---
        Label group2 = createGroupLabel("模式选择");

        Button btnQuick = createBtn("极速快传");
        btnQuick.setOnAction(e -> {
            mainStage.switchView(quickShareView.getView());
            activate(btnQuick);
        });

        Button btnLocal = createBtn("本地目录共享");
        btnLocal.setOnAction(e -> {
            mainStage.switchView(localShareView.getView());
            activate(btnLocal);
        });

        Button btnRemote = createBtn("远程全盘访问");
        btnRemote.setOnAction(e -> {
            mainStage.switchView(remoteDiskView.getView());
            activate(btnRemote);
        });

        // --- 导航区 3: 系统 ---
        Label group3 = createGroupLabel("系统");
        Button btnLog = createBtn("日志记录");
        btnLog.setOnAction(e -> {
            mainStage.switchView(logView.getView());
            activate(btnLog);
        });

        Button btnSet = createBtn("系统设置");
        btnSet.setOnAction(e -> {
            mainStage.switchView(settingsView.getView());
            activate(btnSet);
        });

        view.getChildren().addAll(
                logo, new Separator(),
                group1, btnDash, btnUser,btnRadar,
                new Separator(),
                group2, btnQuick, btnLocal, btnRemote,
                new Separator(),
                group3, btnLog, btnSet
        );

        btnDash.fire();
    }
    /**
     * 创建分组小标题
     */
    private Label createGroupLabel(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add(Styles.TEXT_CAPTION); // 小号字体
        lbl.setStyle("-fx-text-fill: -color-fg-muted;");
        lbl.setPadding(new Insets(10, 0, 0, 5));
        return lbl;
    }

    private void activate(Button btn) {
        if (activeBtn != null) {
            // 还原上一个按钮：透明背景，无边框
            activeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-default;");
            activeBtn.setCursor(javafx.scene.Cursor.HAND);
        }

        activeBtn = btn;
        // 选中样式：蓝色背景 (accent-muted)，蓝色文字 (accent-fg)
        // 这里的颜色变量是 AtlantaFX 自带的，深浅色模式通用
        activeBtn.setStyle("-fx-background-color: -color-accent-subtle; -fx-text-fill: -color-accent-fg; -fx-background-radius: 5; -fx-font-weight: bold;");
        activeBtn.setCursor(javafx.scene.Cursor.DEFAULT);
    }
    private Button createBtn(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(10, 15, 10, 15));
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-default;");
        btn.setOnMouseEntered(e -> {
            if (btn != activeBtn) { // 如果不是当前选中的按钮
                btn.setStyle("-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-default; -fx-background-radius: 5;");
            }
        });
        btn.setOnMouseExited(e -> {
            if (btn != activeBtn) {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-default;");
            }
        });

        btn.setCursor(javafx.scene.Cursor.HAND);
        return btn;
    }

    public VBox getView() { return view; }
}