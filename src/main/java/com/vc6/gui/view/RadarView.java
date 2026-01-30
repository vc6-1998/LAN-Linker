package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.core.service.DiscoveryService;
import com.vc6.model.RemoteDevice;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

public class RadarView {

    private final BorderPane view;
    private Timer scanTimer;

    public RadarView() {
        this.view = new BorderPane();
        initView();

        // 启动自动扫描 (每5秒一次，保持列表新鲜)
        startAutoScan();
    }

    private void initView() {
        view.setPadding(new Insets(30));

        // 1. 顶部：标题 + 刷新按钮
        HBox topBox = new HBox(20);
        topBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("局域网设备扫描");
        title.getStyleClass().add(Styles.TITLE_3);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button scanBtn = new Button("立即刷新");
        scanBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.ACCENT);
        scanBtn.setGraphic(new Label("⟳"));
        scanBtn.setFocusTraversable(false);
        scanBtn.setOnAction(e -> {
            DiscoveryService.getInstance().scan();
            // 给个小反馈
            scanBtn.setText("扫描中...");
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override public void run() { Platform.runLater(() -> scanBtn.setText("立即刷新")); }
            }, 1000);
        });

        topBox.getChildren().addAll(title, spacer, scanBtn);
        view.setTop(topBox);

        // 2. 中部：设备列表表格
        TableView<RemoteDevice> table = new TableView<>();
        // 绑定数据源 (Service 里的列表是 Observable 的，会自动更新)
        Label emptyLabel = new Label("当前无可连接设备");
        table.setPlaceholder(emptyLabel);
        table.setSelectionModel(null);
        table.setItems(DiscoveryService.getInstance().getFoundDevices());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 列 1: 设备名称
        TableColumn<RemoteDevice, String> nameCol = new TableColumn<>("设备名称");
        nameCol.setCellValueFactory(d -> d.getValue().nameProperty());
        nameCol.setMinWidth(120);
        nameCol.setReorderable(false);

        // 列 2: IP 地址
        TableColumn<RemoteDevice, String> ipCol = new TableColumn<>("IP 地址");
        ipCol.setCellValueFactory(d -> d.getValue().ipProperty());
        ipCol.setMinWidth(100);
        ipCol.setReorderable(false);
        // 列 3: 端口
        TableColumn<RemoteDevice, String> portCol = new TableColumn<>("服务端口");
        portCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getPort())));
        portCol.setMinWidth(120);
        portCol.setReorderable(false);
        // 列 4: 操作 (连接按钮)
        TableColumn<RemoteDevice, Void> actionCol = new TableColumn<>("");
        actionCol.setReorderable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("连接");
            {
                btn.getStyleClass().addAll(Styles.SMALL, Styles.SUCCESS);
                btn.setFocusTraversable(false);
                btn.setOnAction(e -> {
                    RemoteDevice device = getTableView().getItems().get(getIndex());
                    openBrowser(device.getUrl());
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
                setAlignment(Pos.CENTER);
            }
        });
        actionCol.setMinWidth(80);

        table.getColumns().addAll(nameCol, ipCol, portCol, actionCol);

        view.setCenter(table);
        BorderPane.setMargin(table, new Insets(20, 0, 0, 0));
    }

    private void startAutoScan() {
        scanTimer = new Timer(true);
        // 延迟1秒开始，每5秒扫描一次
        scanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // 只有当界面可见时才扫描 (简单判断 scene 是否存在)
                if (view.getScene() != null) {
                    DiscoveryService.getInstance().scan();
                }
            }
        }, 1000, 5000);
    }

    private void openBrowser(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BorderPane getView() { return view; }
}