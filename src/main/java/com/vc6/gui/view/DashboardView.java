package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.core.NettyServer;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import com.vc6.utils.IpUtils;
import com.vc6.utils.QrCodeGenerator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.util.Timer;
import java.util.TimerTask;

public class DashboardView {

    private final VBox view;
    private HBox connectionCard;
    // UI 组件
    private Label statusLabel;
    private Circle statusIndicator;
    private Button stopBtn;

    // 连接卡片组件 (双态)
    private VBox infoBox;      // 右侧信息容器
    private ImageView qrView;  // 左侧图片容器
    private Label infoTitle;   // 卡片标题

    // 流量图表
    private AreaChart<Number, Number> trafficChart;
    private XYChart.Series<Number, Number> uploadSeries;
    private XYChart.Series<Number, Number> downloadSeries;

    private Label speedLabel;

    private Timer monitorTimer;
    private int timeSeconds = 0;

    public DashboardView() {
        this.view = new VBox(20);
        initView();

        // 监听模式变化，触发界面刷新
        AppConfig.getInstance().serverModeProperty().addListener((obs, oldVal, newVal) -> {
            updateDashboardState(newVal);
        });

        // 初始状态刷新
        updateDashboardState(AppConfig.getInstance().getServerMode());

        startTrafficMonitor();
    }

    private void initView() {
        view.setPadding(new Insets(30));
        view.getChildren().addAll(createHeader(), createConnectionCard(), createTrafficChart());
    }

    // --- 顶部 Header ---
    private HBox createHeader() {
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("服务器控制台");
        title.getStyleClass().add(Styles.TITLE_3);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusIndicator = new Circle(6);
        statusIndicator.setStyle("-fx-fill: -color-danger-fg;");

        statusLabel = new Label("已停止");
        statusLabel.getStyleClass().add(Styles.TEXT_MUTED);

        stopBtn = new Button("停止所有服务");
        stopBtn.getStyleClass().add(Styles.DANGER);
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> handleStopAll());

        header.getChildren().addAll(title, spacer, statusIndicator, statusLabel, stopBtn);
        return header;
    }

    // --- 连接卡片 (核心改造) ---
    private HBox createConnectionCard() {
        connectionCard = new HBox(20); // 赋值给成员变量
        connectionCard.setPadding(new Insets(30)); // 增加内边距，让卡片看起来更宽敞
        connectionCard.getStyleClass().addAll(Styles.ELEVATED_1, Styles.BG_DEFAULT);
        connectionCard.setStyle("-fx-background-radius: 10;");
        connectionCard.setMinHeight(180);
        connectionCard.setAlignment(Pos.CENTER); // 【关键】默认内容居中

        qrView = new ImageView();
        qrView.setFitWidth(120);
        qrView.setFitHeight(120);

        infoBox = new VBox(10);
        infoBox.setAlignment(Pos.CENTER_LEFT); // 默认左对齐，后面会动态改

        HBox.setHgrow(infoBox, Priority.ALWAYS);
        // 初始不添加任何子元素，交给 updateDashboardState 去决定
        return connectionCard;
    }

    // --- 流量图表 ---
    private VBox createTrafficChart() {
        VBox box = new VBox(10);
        HBox titleBox = new HBox(20);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label chartTitle = new Label("实时流量监控 (MB/s)");
        chartTitle.getStyleClass().add(Styles.TEXT_BOLD);

        speedLabel = new Label("↑ 0 KB/s   ↓ 0 KB/s");
        speedLabel.setStyle("-fx-font-family: 'monospace'; -fx-font-size: 15px;");

        titleBox.getChildren().addAll(chartTitle, speedLabel);

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("时间");
        xAxis.setAutoRanging(false); // 手动控制范围，实现流动效果
        xAxis.setTickLabelFormatter(new javafx.util.StringConverter<Number>() {
            @Override
            public String toString(Number object) {
                // 将秒数转换为 HH:mm:ss
                long time = object.longValue() * 1000;
                return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(time));
            }
            @Override
            public Number fromString(String string) { return 0; }
        });


        NumberAxis yAxis = new NumberAxis();

        trafficChart = new AreaChart<>(xAxis, yAxis);
        trafficChart.setAnimated(false);
        trafficChart.setCreateSymbols(false);
        trafficChart.setLegendVisible(true);
        trafficChart.setPrefHeight(300);

        trafficChart.setStyle("-color-chart-1: #28a745; -color-chart-2: #007bff;");

        uploadSeries = new XYChart.Series<>();
        uploadSeries.setName("上传速度");
        downloadSeries = new XYChart.Series<>();
        downloadSeries.setName("下载速度");
        trafficChart.getData().addAll(uploadSeries, downloadSeries);

        box.getChildren().addAll(titleBox, trafficChart);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    // ================= 状态更新逻辑 (核心) =================

    private void updateDashboardState(ServerMode mode) {
        if (mode == ServerMode.STOPPED) {
            // 停止状态：虽然也要查 IP，但我们可以在 renderDiagnosticView 内部做异步
            // 这里先切 UI 结构
            Platform.runLater(() -> {
                statusLabel.setText("已停止");
                statusIndicator.setStyle("-fx-fill: -color-danger-fg;");
                stopBtn.setDisable(true);
                renderDiagnosticView(); // 调用下面的异步版方法
            });
        } else {
            // 运行状态：查 IP + 生成二维码
            Platform.runLater(() -> {
                statusLabel.setText("运行中 - " + mode.getDescription());
                statusIndicator.setStyle("-fx-fill: -color-success-fg;");
                stopBtn.setDisable(false);

                // 先显示个“加载中”占位
                connectionCard.getChildren().clear();
                connectionCard.getChildren().add(new Label("正在生成连接信息..."));
            });

            // 【异步加载连接信息】
            new Thread(() -> {
                String ip = IpUtils.getLocalIp();
                int port = AppConfig.getInstance().getPort();
                String url = "http://" + ip + ":" + port;
                Image qr = QrCodeGenerator.generate(url, 200);

                Platform.runLater(() -> renderRunningView(ip, url, qr)); // 查完再渲染
            }).start();
        }
    }

    /**
     * 渲染状态 1: 预检模式 (Pre-flight Check)
     */
    private void renderDiagnosticView() {
        // 1. 先把架子搭好 (显示“检测中...”)
        connectionCard.getChildren().clear();
        connectionCard.getChildren().add(infoBox);
        infoBox.setAlignment(Pos.CENTER);
        infoBox.getChildren().clear();

        Label loadingLabel = new Label("正在检测网络环境...");
        loadingLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: -color-fg-muted;");
        infoBox.getChildren().add(loadingLabel);

        // 2. 启动后台线程查 IP 和 端口
        new Thread(() -> {
            String ip = IpUtils.getLocalIp();
            String host = IpUtils.getHostName();
            int port = AppConfig.getInstance().getPort();
            boolean available = IpUtils.isPortAvailable(port);

            // 3. 查完回 UI 线程填数据
            Platform.runLater(() -> {
                // 如果用户已经切换界面或者启动服务了，就别刷新了
                if (AppConfig.getInstance().getServerMode() != ServerMode.STOPPED) return;

                infoBox.getChildren().clear(); // 清掉“检测中”

                // --- 下面是你原来的 UI 代码，直接搬进来 ---
                Label ipLabel = new Label("IP: " + ip);
                ipLabel.getStyleClass().addAll(Styles.TITLE_1, Styles.ACCENT);

                Label hostLabel = new Label("Host: " + host);
                hostLabel.getStyleClass().add(Styles.TEXT_MUTED);

                HBox portBox = new HBox(15);
                portBox.setAlignment(Pos.CENTER);

                Label portLabel = new Label("端口: " + port);
                portLabel.getStyleClass().add(Styles.TEXT_BOLD);

                Label portStatus = new Label();
                if (available) {
                    portStatus.setText("✔ 端口可用");
                    portStatus.setStyle("-fx-text-fill: -color-success-fg;");
                } else {
                    portStatus.setText("✘ 端口被占用");
                    portStatus.setStyle("-fx-text-fill: -color-danger-fg;");
                }

                Button refreshBtn = new Button("刷新");
                refreshBtn.getStyleClass().add(Styles.SMALL);
                // 刷新就是重新调一次自己
                refreshBtn.setOnAction(e -> renderDiagnosticView());

                portBox.getChildren().addAll(portLabel, portStatus, refreshBtn);
                infoBox.getChildren().addAll(ipLabel, hostLabel, portBox);
            });
        }).start();
    }

    /**
     * 渲染状态 2: 运行模式 (Connection Info)
     */
    private void renderRunningView(String ip, String url, Image qr) {
        // 1. 布局调整：恢复二维码 + 信息框
        connectionCard.getChildren().clear();
        connectionCard.getChildren().addAll(qrView, infoBox);
        infoBox.setAlignment(Pos.CENTER_LEFT); // 恢复左对齐
        infoBox.getChildren().clear();
        qrView.setImage(qr);

        Label title = new Label("连接方式"); // 这个标题保留，或者也可以删掉看你喜好
        title.getStyleClass().add(Styles.TITLE_4);

        // 链接框
        HBox linkBox = new HBox(10);
        TextField linkField = new TextField(url);
        linkField.setEditable(false);

        linkField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(linkField, Priority.ALWAYS);

        linkField.setStyle("-fx-font-family: 'monospace';");

        Button copyBtn = new Button("复制");
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
        });
        linkBox.getChildren().addAll(linkField, copyBtn);

        Label hint = new Label("同一网络环境下，扫码或输入网址即可访问");
        hint.getStyleClass().add(Styles.TEXT_MUTED);

        infoBox.getChildren().addAll(title, linkBox, hint);

    }

    private void handleStopAll() {
        stopBtn.setDisable(true);
        new Thread(() -> {
            NettyServer.getInstance().stop();
        }).start();
    }

    private void startTrafficMonitor() {
        monitorTimer = new Timer(true);
        monitorTimer.schedule(new TimerTask() {
            private final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            @Override
            public void run() {
                if (view.getScene() == null) return;
                try {
                    long writeBytes = NettyServer.getTrafficHandler().trafficCounter().lastWriteThroughput();
                    long readBytes = NettyServer.getTrafficHandler().trafficCounter().lastReadThroughput();

                    double downloadSpeed = writeBytes / (1024.0 * 1024.0);
                    double uploadSpeed = readBytes / (1024.0 * 1024.0);


                    long nowSeconds = System.currentTimeMillis() / 1000;
                    Platform.runLater(() -> {
                        uploadSeries.getData().add(new XYChart.Data<>(nowSeconds, uploadSpeed));
                        downloadSeries.getData().add(new XYChart.Data<>(nowSeconds, downloadSpeed));

                        // 滚动 X 轴范围 (最近 60 秒)
                        NumberAxis xAxis = (NumberAxis) trafficChart.getXAxis();
                        xAxis.setLowerBound(nowSeconds - 60);
                        xAxis.setUpperBound(nowSeconds);

                        // 移除旧数据 (防止内存溢出)
                        if (uploadSeries.getData().size() > 65) {
                            uploadSeries.getData().remove(0);
                            downloadSeries.getData().remove(0);
                        }
                        String upStr = formatSpeed(readBytes);
                        String downStr = formatSpeed(writeBytes);
                        speedLabel.setText(String.format("↑ %s   ↓ %s", upStr, downStr));
                    });
                } catch (Exception e) {}
            }
        }, 1000, 1000);
    }

    private String formatSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024) return bytesPerSec + " B/s";
        double kb = bytesPerSec / 1024.0;
        if (kb < 1024) return String.format("%.1f KB/s", kb);
        return String.format("%.1f MB/s", kb / 1024.0);
    }

    public VBox getView() { return view; }
    public void refresh() {
        // 读取当前模式，强制刷新一次 UI
        updateDashboardState(AppConfig.getInstance().getServerMode());
    }
}