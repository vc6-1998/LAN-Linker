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
        setupNumericField(portField, AppConfig.getInstance().portProperty(), 1, 65535);
        portField.setPrefWidth(80);

        portField.disableProperty().bind(
                AppConfig.getInstance().serverModeProperty().isNotEqualTo(ServerMode.STOPPED)
        );

        // 【逻辑3】状态提示标签
        Label portStatus = new Label();
        portStatus.setStyle("-fx-font-size: 12px;");

        AppConfig.getInstance().portProperty().addListener((obs, old, newVal) -> {
            checkPortStatus(String.valueOf(newVal), portStatus);
            MessageUtils.showToast("端口已更新为: " + newVal);
        });

        checkPortStatus(portField.getText(), portStatus);

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

        TextField titleField = new TextField(AppConfig.getInstance().getdeviceName());
        titleField.setPrefWidth(200);
        titleField.textProperty().addListener((o, old, val) -> {
            if (val != null && !val.isEmpty()) {
                AppConfig.getInstance().setdeviceName(val);
                ConfigStore.save();
            }
        });

        addGridRow(grid, 2, "设备名称:", titleField);

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
        authSwitch.disableProperty().bind(
                AppConfig.getInstance().serverModeProperty().isEqualTo(ServerMode.REMOTE_DISK)
        );
        addGridRow(grid, 0, "安全网关:", authSwitch);

        // 2. PIN 码设置
        PasswordField pinField = new PasswordField();
        pinField.setPromptText("4-20位字母数字");
        pinField.setPrefWidth(150); // 稍微加宽一点，因为20位比较长
        setupPinField(pinField, AppConfig.getInstance().remotePinProperty());
        addGridRow(grid, 1, "访问 PIN 码:", pinField);

        // 3. 会话有效期设置
        ComboBox<String> expiryBox = new ComboBox<>();
        expiryBox.getItems().addAll("1 小时", "1 天","7 天","30 天","365 天");

        // 映射逻辑
        int expiryTime = AppConfig.getInstance().getSessionExpiryTime();
        if (expiryTime == 1) expiryBox.getSelectionModel().select(0);
        else if (expiryTime == 2) expiryBox.getSelectionModel().select(1);
        else if (expiryTime == 3) expiryBox.getSelectionModel().select(2);
        else if (expiryTime == 4) expiryBox.getSelectionModel().select(3);
        else if (expiryTime == 5) expiryBox.getSelectionModel().select(4);

        expiryBox.valueProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            if (val.contains("1 小时")) AppConfig.getInstance().setSessionExpiryTime(1);
            else if (val.contains("1 天")) AppConfig.getInstance().setSessionExpiryTime(2);
            else if (val.contains("7 天")) AppConfig.getInstance().setSessionExpiryTime(3);
            else if (val.contains("30 天")) AppConfig.getInstance().setSessionExpiryTime(4);
            else if (val.contains("365 天")) AppConfig.getInstance().setSessionExpiryTime(5);
        });
        addGridRow(grid, 2, "登录有效期:", expiryBox);

        SimpleToggleSwitch discoveryCheck = new SimpleToggleSwitch("允许被其它设备扫描");
        discoveryCheck.selectedProperty().bindBidirectional(AppConfig.getInstance().discoveryEnabledProperty());
        addGridRow(grid, 3, "服务广播:", discoveryCheck);

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
        cleanBtn.getStyleClass().addAll(Styles.DANGER);
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
        addGridRow(grid, 0, "快传缓存路径:", pathBox);

        ComboBox<String> expireCombo = new ComboBox<>();
        expireCombo.getItems().addAll("1 小时后", "12 小时后", "24 小时后", "7 天后","永不清理");

        // 初始化选中状态
        int hours = AppConfig.getInstance().getQuickShareExpireHours();
        if (hours == 1) expireCombo.getSelectionModel().select(0);
        else if (hours == 12) expireCombo.getSelectionModel().select(1);
        else if (hours == 24) expireCombo.getSelectionModel().select(2);
        else if (hours == 24*7) expireCombo.getSelectionModel().select(3);
        else expireCombo.getSelectionModel().select(4);

        // 绑定监听
        expireCombo.valueProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            int newHours = 0;
            if (val.contains("1 小时")) newHours = 1;
            else if (val.contains("12 小时")) newHours = 12;
            else if (val.contains("24 小时")) newHours = 24;
            else if (val.contains("7 天")) newHours = 24*7;

            AppConfig.getInstance().setQuickShareExpireHours(newHours);
            MessageUtils.showToast("清理时间已更新");
        });
        addGridRow(grid, 1, "自动清理时间:", expireCombo);

        // 2.3 安全限制
        TextField fileLimitField = new TextField(String.valueOf(AppConfig.getInstance().getMaxFileSizeMb()));
        fileLimitField.setPrefWidth(80);
        // 使用工具方法：范围 1MB - 2047MB (防止 Netty 溢出)
        setupNumericField(fileLimitField, AppConfig.getInstance().maxFileSizeMbProperty(), 1, 2047);

        // 2. 文本字数上限输入框
        TextField textLimitField = new TextField(String.valueOf(AppConfig.getInstance().getMaxTextLength()));
        textLimitField.setPrefWidth(80);
        // 使用工具方法：范围 1字 - 1,000,000字
        setupNumericField(textLimitField, AppConfig.getInstance().maxTextLengthProperty(), 1, 1000000);

        // --- 布局组装 ---
        HBox fileLimitBox = new HBox(10, fileLimitField, new Label("MB"));
        fileLimitBox.setAlignment(Pos.CENTER_LEFT);
        addGridRow(grid, 2, "单文件上限:", fileLimitBox);

        HBox textLimitBox = new HBox(10, textLimitField, new Label("字"));
        textLimitBox.setAlignment(Pos.CENTER_LEFT);
        addGridRow(grid, 3, "文本字数:", textLimitBox);
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

//        Label logPathLabel = new Label("运行日志:");
        TextField logPathField = new TextField(new File("logs").getAbsolutePath());
        logPathField.setEditable(false);
        HBox.setHgrow(logPathField, Priority.ALWAYS);

        Button openLogBtn = new Button("打开文件夹");
        openLogBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        openLogBtn.setOnAction(e -> {
            try {
                File logDir = new File("logs");
                if (!logDir.exists()) logDir.mkdirs();
                java.awt.Desktop.getDesktop().open(logDir);
            } catch (Exception ex) {
                MessageUtils.showToast("打开失败");
            }
        });
        HBox logPathBox = new HBox(10, logPathField, openLogBtn);
        addGridRow(grid, 1, "运行日志:",logPathBox);

        String configPath = new File("config.properties").getAbsolutePath();
        TextField pathField = new TextField(configPath);
        pathField.setEditable(false);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button openBtn = new Button("打开所在文件夹");
        openBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        openBtn.setOnAction(e -> openFile(new File(configPath).getParentFile()));

        HBox pathBox = new HBox(10, pathField, openBtn);
        addGridRow(grid, 2, "配置文件:", pathBox);

        String userPath = new File("user.properties").getAbsolutePath();
        TextField userpathField = new TextField(userPath);
        userpathField.setEditable(false);
        HBox.setHgrow(userpathField, Priority.ALWAYS);

        Button userOpenBtn = new Button("打开所在文件夹");
        userOpenBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        userOpenBtn.setOnAction(e -> openFile(new File(userPath).getParentFile()));

        HBox userpathBox = new HBox(10, userpathField, userOpenBtn);
        addGridRow(grid, 3, "用户配置:", userpathBox);

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
        addGridRow(grid, 4, "",resetBtn);
        return grid;
    }
    private Node createAboutSettings() {
        VBox box = new VBox(15);

// 帮助按钮
        Button helpBtn = new Button("📖 查看使用说明");
        helpBtn.getStyleClass().add(Styles.ACCENT);
        helpBtn.setOnAction(e -> showHelpDialog());

        Label appName = new Label("LAN Linker v2.0");
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
        若两台电脑均安装了该程序，则可在“连接其它服务”栏里搜索到其它已启动服务。
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

    private void setupNumericField(TextField tf, javafx.beans.property.Property<Number> property, long min, long max) {
        // 1. 只能输入数字
        tf.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) tf.setText(val.replaceAll("[^\\d]", ""));
        });

        // 2. 失去焦点自动应用
        tf.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applyNumericValue(tf, property, min, max);
        });

        // 3. 按回车自动应用 (通过请求父容器焦点来触发失焦)
        tf.setOnAction(e -> tf.getParent().requestFocus());
    }

    private void applyNumericValue(TextField tf, javafx.beans.property.Property<Number> property, long min, long max) {
        String text = tf.getText();
        if (text.isEmpty()) {
            tf.setText(String.valueOf(property.getValue()));
            return;
        }
        try {
            long val = Long.parseLong(text);
            if (val < min) val = min;
            if (val > max) val = max;

            // 如果值真的发生了变化，这行代码会触发我们在 createGeneralSettings 里写的那个监听器
            if (val != property.getValue().longValue()) {
                property.setValue(val);
            }

            // 无论值变没变，都把文本框文字修正一下（比如把 99999 变回 65535）
            tf.setText(String.valueOf(val));
        } catch (Exception e) {
            tf.setText(String.valueOf(property.getValue()));
        }
    }

    private void setupPinField(PasswordField pf, javafx.beans.property.StringProperty property) {
        // 1. 实时过滤：只允许字母和数字，且限制最大 20 位
        pf.setText(property.get());
        pf.textProperty().addListener((obs, old, val) -> {
            // 如果不符合正则表达式（字母数字）或者超过20位，则还原
            if (!val.matches("[a-zA-Z0-9]*") || val.length() > 20) {
                pf.setText(old);
            }
        });

        // 2. 失去焦点时校验长度
        pf.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String val = pf.getText();
                // 如果用户输入了内容但少于4位
                if (!val.isEmpty() && val.length() < 4) {
                    MessageUtils.showError("设置失败", "PIN 码长度必须在 4-20 位之间！");
                    pf.setText(property.get()); // 还原回上次保存的有效值
                } else {
                    // 合法长度（4-20位）或 设为空（表示取消密码保护）
                    if (!val.equals(property.get())) {
                        property.set(val);
                        MessageUtils.showToast("访问 PIN 码已更新");
                    }
                }
            }
        });

        // 按回车触发失焦
        pf.setOnAction(e -> pf.getParent().requestFocus());
    }

    public VBox getView() { return view; }
}