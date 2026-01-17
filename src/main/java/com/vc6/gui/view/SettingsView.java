package com.vc6.gui.view;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import com.vc6.core.NettyServer;
import com.vc6.core.persistence.ConfigStore;
import com.vc6.gui.component.SimpleToggleSwitch;
import com.vc6.model.AppConfig;
import com.vc6.utils.IpUtils;
import com.vc6.model.ServerMode;
import com.vc6.utils.MessageUtils;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SettingsView {

    private final VBox view;

    public SettingsView() {
        this.view = new VBox();
        initView();
    }

    private void initView() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.getStyleClass().add(Styles.BG_DEFAULT);

        Label title = new Label("系统设置");
        title.getStyleClass().add(Styles.TITLE_3);

        content.getChildren().addAll(
                title,
                createSection("常规", createGeneralSettings()),
                createSection("安全", createSecuritySettings()),
                createSection("传输", createStorageSettings()),
                createSection("外观", createAppearanceSettings()),
                createSection("系统", createSystemSettings()),
                createSection("关于", createAboutSettings())
        );

        scrollPane.setContent(content);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        view.getChildren().add(scrollPane);
    }


    private void checkPortStatus(String portStr, Label label) {
        try {
            int port = Integer.parseInt(portStr);
            boolean available = IpUtils.isPortAvailable(port);

            // 特殊情况：如果该端口正是我们自己正在运行的端口，那它虽然“不可用”，但也是正常的
            int currentRunningPort = AppConfig.getInstance().getPort();
            boolean isSelf = (port == currentRunningPort) && NettyServer.getInstance().isRunning();

            if (isSelf) {
                label.setText("✔ 当前正在运行");
                label.setStyle("-fx-text-fill: -color-success-fg;");
            } else if (available) {
                label.setText("✔ 端口可用");
                label.setStyle("-fx-text-fill: -color-success-fg;");
            } else {
                label.setText("✘ 端口被占用");
                label.setStyle("-fx-text-fill: -color-danger-fg;");
            }
        } catch (Exception e) {
            label.setText("");
        }
    }
    // ================= 1. 常规设置 (网络 + 系统) =================
    private Node createGeneralSettings() {
        GridPane grid = createGrid();

        // 1.1 服务端口 (自动保存 + 自动检测)
        HBox portBox = new HBox(10);
        portBox.setAlignment(Pos.CENTER_LEFT);

        TextField portField = new TextField(String.valueOf(AppConfig.getInstance().getPort()));
        makeNumeric(portField); // 限制数字
        portField.setPrefWidth(80);

        portField.disableProperty().bind(
                AppConfig.getInstance().serverModeProperty().isNotEqualTo(ServerMode.STOPPED)
        );
        // 状态提示标签
        Label portStatus = new Label();
        portStatus.setStyle("-fx-font-size: 12px;");

        // 初始化时检查一次
        checkPortStatus(portField.getText(), portStatus);

        // 【核心】监听焦点丢失事件 -> 自动保存
        portField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // 失去焦点 (newVal = false)
                String text = portField.getText();
                if (!text.isEmpty()) {
                    int newPort = Integer.parseInt(text);

                    // 只有值真的变了才提示
                    if (newPort != AppConfig.getInstance().getPort()) {
                        AppConfig.getInstance().setPort(newPort);
                        // ConfigStore.save() 会由 AppEntry 自动触发
                        MessageUtils.showToast("端口已更新");
                    }
                    // 执行检测
                    checkPortStatus(text, portStatus);
                }
            }
        });

        // 按回车也可以触发保存 (通过转移焦点)
        portField.setOnAction(e -> grid.requestFocus());

        portBox.getChildren().addAll(portField, portStatus);
        addGridRow(grid, 0, "服务端口:", portBox);
        // 1.2 优先网卡
        ComboBox<String> netBox = new ComboBox<>();
        netBox.getItems().add("Auto (自动检测)");
        netBox.getItems().addAll(getNetworkInterfaces()); // 扫描网卡
        netBox.setValue(AppConfig.getInstance().getPreferredNetworkInterface());
        netBox.setPrefWidth(250);
        netBox.valueProperty().addListener((obs, old, val) -> {
            if (val != null) AppConfig.getInstance().setPreferredNetworkInterface(val);
        });
        addGridRow(grid, 1, "优先 IP:", netBox);

        TextField titleField = new TextField(AppConfig.getInstance().getWebTitle());
        titleField.setPrefWidth(200);
        titleField.textProperty().addListener((o, old, val) -> {
            if (val != null && !val.isEmpty()) {
                AppConfig.getInstance().setWebTitle(val);
                ConfigStore.save();
            }
        });

        addGridRow(grid, 2, "网页名称:", titleField);

        // 1.3 系统集成
        SimpleToggleSwitch trayCheck = new SimpleToggleSwitch("关闭主窗口时最小化到托盘");
        trayCheck.selectedProperty().bindBidirectional(AppConfig.getInstance().minimizeToTrayProperty());
        addGridRow(grid, 3, "关闭设置:", trayCheck);



        return grid;
    }

    private Node createSecuritySettings() {
        GridPane grid = new GridPane();
        grid.setHgap(20); grid.setVgap(15);

        // 1. 全局保护开关
        SimpleToggleSwitch authSwitch = new SimpleToggleSwitch("启用全局网页访问保护");
        authSwitch.selectedProperty().bindBidirectional(AppConfig.getInstance().globalAuthEnabledProperty());
        addGridRow(grid, 0, "安全网关:", authSwitch);

        // 2. PIN 码设置
        PasswordField pinField = new PasswordField();
        pinField.setPromptText("4-6位数字");
        pinField.setPrefWidth(120);
        pinField.setText(AppConfig.getInstance().getRemotePin());
        pinField.textProperty().addListener((obs, old, val) -> AppConfig.getInstance().setRemotePin(val));
        addGridRow(grid, 1, "访问 PIN 码:", pinField);

        // 3. 会话有效期设置
        ComboBox<String> expiryBox = new ComboBox<>();
        expiryBox.getItems().addAll("1 天内免登录", "7 天内免登录");

        // 映射逻辑
        int currentDays = AppConfig.getInstance().getSessionExpiryDays();
        if (currentDays == 1) expiryBox.getSelectionModel().select(0);
        else if (currentDays == 7) expiryBox.getSelectionModel().select(1);

        expiryBox.valueProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            if (val.contains("1")) AppConfig.getInstance().setSessionExpiryDays(1);
            else if (val.contains("7")) AppConfig.getInstance().setSessionExpiryDays(7);
        });
        addGridRow(grid, 2, "登录有效期:", expiryBox);

        return grid;
    }
    // ================= 2. 传输与存储 =================
    private Node createStorageSettings() {
        GridPane grid = createGrid();

        // 2.1 快传路径
        TextField pathField = new TextField();
        // 绑定显示
        pathField.textProperty().bind(AppConfig.getInstance().quickSharePathProperty());


        Button openBtn = new Button("打开");
        openBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        openBtn.setOnAction(e -> openFile(new File(pathField.getText())));

        Button changeBtn = new Button("更改");
        changeBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("选择快传缓存目录");

            File current = new File(AppConfig.getInstance().getQuickSharePath());
            if (current.exists()) dc.setInitialDirectory(current);

            File selected = dc.showDialog(view.getScene().getWindow());
            if (selected != null) {
                AppConfig.getInstance().setQuickSharePath(selected.getAbsolutePath());
                MessageUtils.showToast("缓存路径已更新");
            }
        });

        Button cleanBtn = new Button("清理缓存");
        cleanBtn.getStyleClass().addAll(Styles.SMALL, Styles.DANGER);
        cleanBtn.setOnAction(e -> {
            File dir = new File(AppConfig.getInstance().getQuickSharePath());
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    int count = 0;
                    for (File f : files) {
                        if (f.delete()) count++;
                    }
                    MessageUtils.showToast("清理完成，共删除 " + count + " 个文件");
                }
            } else {
                MessageUtils.showToast("缓存目录为空");
            }
        });

        HBox pathBox = new HBox(10, pathField, openBtn,changeBtn,cleanBtn);
        addGridRow(grid, 0, "快传缓存:", pathBox);


        // 2.3 安全限制
        TextField fileLimit = new TextField(String.valueOf(AppConfig.getInstance().getMaxFileSizeMb()));
        makeNumeric(fileLimit);
        fileLimit.setPrefWidth(80);
        fileLimit.textProperty().addListener((o, old, val) -> {
            if(!val.isEmpty()) AppConfig.getInstance().setMaxFileSizeMb(Long.parseLong(val));
        });

        TextField textLimit = new TextField(String.valueOf(AppConfig.getInstance().getMaxTextLength()));
        makeNumeric(textLimit);
        textLimit.setPrefWidth(80);
        textLimit.textProperty().addListener((o, old, val) -> {
            if(!val.isEmpty()) AppConfig.getInstance().setMaxTextLength(Integer.parseInt(val));
        });

        addGridRow(grid, 2, "单文件上限:", new HBox(10, fileLimit, new Label("MB")));
        addGridRow(grid, 3, "文本字数:", new HBox(10, textLimit, new Label("字")));

        return grid;
    }

    // ================= 3. 外观与显示 =================
    private Node createAppearanceSettings() {
        GridPane grid = createGrid();

        // 3.1 主题
        ToggleGroup themeGroup = new ToggleGroup();
        ToggleButton darkBtn = new ToggleButton("深色");
        ToggleButton lightBtn = new ToggleButton("浅色");
        darkBtn.setToggleGroup(themeGroup);
        lightBtn.setToggleGroup(themeGroup);

        darkBtn.getStyleClass().add(Styles.LEFT_PILL);
        lightBtn.getStyleClass().add(Styles.RIGHT_PILL);

        if (AppConfig.getInstance().isDarkMode()) darkBtn.setSelected(true);
        else lightBtn.setSelected(true);

        themeGroup.selectedToggleProperty().addListener((obs, old, val) -> {
            if (val == null) {
                old.setSelected(true); // 禁止取消
            } else {
                boolean isDark = (val == darkBtn);
                AppConfig.getInstance().setDarkMode(isDark);
                Application.setUserAgentStylesheet(isDark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
            }
        });
        addGridRow(grid, 0, "界面主题:", new HBox(darkBtn, lightBtn));

        // 3.2 缩放
        Slider scaleSlider = new Slider(80, 150, 100);
        scaleSlider.setShowTickMarks(true);
        scaleSlider.setShowTickLabels(true);
        scaleSlider.setMajorTickUnit(25);
        scaleSlider.setSnapToTicks(true);
        scaleSlider.setValue(AppConfig.getInstance().getUiScalePercent());

        // 实时应用缩放
        scaleSlider.valueProperty().addListener((obs, old, val) -> {
            int scale = val.intValue();
            AppConfig.getInstance().setUiScalePercent(scale);
            if (view.getScene() != null) {
                double fontSize = 14 * (scale / 100.0);
                view.getScene().getRoot().setStyle("-fx-font-size: " + fontSize + "px;");
            }
        });
        addGridRow(grid, 1, "界面缩放:", scaleSlider);

        return grid;
    }

    // ================= 4. 关于与帮助 =================
    private Node createSystemSettings()
    {
        GridPane grid = createGrid();
        SimpleToggleSwitch debugCheck = new SimpleToggleSwitch("DEBUG 模式 (显示所有请求)");
        debugCheck.selectedProperty().bindBidirectional(AppConfig.getInstance().debugModeProperty());
        addGridRow(grid, 0, "日志设置:",debugCheck);

        String configPath = new File("config.properties").getAbsolutePath();
        TextField pathField = new TextField(configPath);
        pathField.setEditable(false);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button openBtn = new Button("打开所在文件夹");
        openBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        openBtn.setOnAction(e -> openFile(new File(configPath).getParentFile()));

        HBox pathBox = new HBox(10, pathField, openBtn);
        addGridRow(grid, 1, "配置文件:", pathBox);

        String userPath = new File("user.properties").getAbsolutePath();
        TextField userpathField = new TextField(userPath);
        userpathField.setEditable(false);
        HBox.setHgrow(userpathField, Priority.ALWAYS);

        Button userOpenBtn = new Button("打开所在文件夹");
        userOpenBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        userOpenBtn.setOnAction(e -> openFile(new File(userPath).getParentFile()));

        HBox userpathBox = new HBox(10, userpathField, userOpenBtn);
        addGridRow(grid, 2, "用户配置:", userpathBox);

        Button resetBtn = new Button("恢复所有设置");
        resetBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER);
        resetBtn.setMaxWidth(Double.MAX_VALUE);

        resetBtn.setOnAction(e -> {
            boolean confirm = MessageUtils.showConfirm(
                    "危险操作",
                    "确定要重置所有设置吗？\n这将会删除配置文件，并立即关闭程序。下次启动将恢复默认状态。"
            );
            if (confirm) {
                File configFile = new File("config.properties");
                if (configFile.exists()) {
                    configFile.delete();
                }
                new File("users.properties").delete();

                MessageUtils.showToast("设置已重置，程序即将关闭...");
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                }, 1500);
            }
        });
        addGridRow(grid, 3, "",resetBtn);
        return grid;
    }
    private Node createAboutSettings() {
        VBox box = new VBox(15);

// 帮助按钮
        Button helpBtn = new Button("📖 查看使用说明");
        helpBtn.getStyleClass().add(Styles.ACCENT);
        helpBtn.setOnAction(e -> showHelpDialog());

        Label appName = new Label("LAN Linker v1.2");
        appName.getStyleClass().add(Styles.TITLE_4);

        TextFlow desc = new TextFlow(
                new Text("Author: vc6-1998\n"),
                new Text("一个基于 JavaFX + Netty 的局域网文件传输神器。")
        );
        Hyperlink gitLink = new Hyperlink("GitHub 开源地址");
        gitLink.setStyle("-fx-border-color: transparent; -fx-padding: 0;");
        gitLink.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(java.net.URI.create("https://github.com/vc6-1998/LAN-Linker"));
            } catch (Exception ex) { ex.printStackTrace(); }
        });


        box.getChildren().addAll(helpBtn, new Separator(), appName, desc,gitLink);
        return box;
    }

    // ================= 辅助方法 =================

    private void showHelpDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("使用说明");
        alert.setHeaderText("如何使用 LAN Linker？");
        alert.setContentText("""
        1. 连接网络
        - 确保所有设备连接同一个 局域网 (不同账号的校园网属于一个局域网，允许互通)。
        - 在“仪表盘”查看本机 IP 和端口，端口可在设置里更改。
        2. 扫码访问
        启动任意服务模式，用手机浏览器扫描仪表盘上的二维码，或直接输入网址。 
        3. 三种模式
        - 极速快传：像聊天一样互发文本、图片和文件(临时存储，支持直接发送、粘贴或拖拽方式)。
        - 本地共享：将电脑上的某个文件夹共享给手机管理。
        - 远程访问：在手机上管理电脑全盘文件（必须设置网关密码）。
        4. 常见问题
        - 网页打不开？请检查电脑防火墙是否放行了 Java 程序。
        - 上传失败？请检查设置里的文件大小限制。
        """);
        alert.show();
    }

    private void makeNumeric(TextField tf) {
        tf.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) tf.setText(val.replaceAll("[^\\d]", ""));
        });
    }

    private void openFile(File file) {
        try { Desktop.getDesktop().open(file); } catch (Exception ignored) {}
    }

    private List<String> getNetworkInterfaces() {
        List<String> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface net = nets.nextElement();
                if (net.isUp() && !net.isLoopback()) {
                    Enumeration<InetAddress> addrs = net.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr.getAddress().length == 4) {
                            list.add(addr.getHostAddress() + " (" + net.getDisplayName() + ")");
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return list;
    }

    private VBox createSection(String titleText, Node body) {
        VBox section = new VBox(10);
        Label header = new Label(titleText);
        header.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.ACCENT);
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.getStyleClass().addAll(Styles.ELEVATED_1, Styles.BG_SUBTLE);
        card.setStyle("-fx-background-radius: 8;");
        card.getChildren().add(body);
        section.getChildren().addAll(header, card);
        return section;
    }

    private GridPane createGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        return grid;
    }

    private void addGridRow(GridPane grid, int row, String labelText, Node control) {
        Label label = new Label(labelText);
        label.setMinWidth(80);
        label.setAlignment(Pos.CENTER_LEFT);
        grid.add(label, 0, row);
        grid.add(control, 1, row);
    }

    public VBox getView() { return view; }
}