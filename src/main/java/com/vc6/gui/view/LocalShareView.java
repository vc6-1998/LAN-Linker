package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.gui.component.ModeActionButton;
import com.vc6.gui.component.SimpleToggleSwitch;
import com.vc6.model.AppConfig;
import com.vc6.model.FileItem;
import com.vc6.model.ServerMode; // 确保导入 ServerMode
import com.vc6.utils.MessageUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class LocalShareView {

    private final BorderPane view;

    // 界面组件
    private Label sharedPathLabel;
    private TextField browsePathField;
    private TableView<FileItem> fileTable;
    private ComboBox<String> historyBox;
    private static final double btnSize = 40;
    private static final String SVG_FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    private static final String SVG_FILE = "M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-7-7z";
    private static final String SVG_REFRESH = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    // 状态变量
    private File currentBrowsingDir;

    public LocalShareView() {
        this.view = new BorderPane();
        initView();

        Platform.runLater(() -> {
            String configPath = AppConfig.getInstance().getRootPath();
            File initialDir = new File(configPath);
            if (!initialDir.exists() || !initialDir.isDirectory()) {
                initialDir = new File(System.getProperty("user.home"));
            }
            refreshFileList(initialDir);
        });

    }

    private void initView() {
        view.setPadding(new Insets(30));

        // 顶部
        VBox topContainer = new VBox(20);
        topContainer.getChildren().addAll(createTitleBar(), createSharedControlBar(), createBrowseControlBar());
        view.setTop(topContainer);

        // 中部
        fileTable = createFileTable();
        view.setCenter(fileTable);
        BorderPane.setMargin(fileTable, new Insets(20, 0, 0, 0));

        // 底部
        HBox bottomArea = createBottomControls();
        BorderPane.setMargin(bottomArea, new Insets(20, 0, 0, 0));
        view.setBottom(bottomArea);
    }

    // ================= UI 构建 =================

    private HBox createTitleBar() {
        HBox box = new HBox();
        Label title = new Label("本地目录共享");
        title.getStyleClass().add(Styles.TITLE_3);
        box.getChildren().add(title);
        return box;
    }

    private HBox createSharedControlBar() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 15, 10, 15));
        box.getStyleClass().addAll(Styles.ELEVATED_1, Styles.BG_ACCENT_MUTED);
        box.setStyle("-fx-background-radius: 8;");

        // 左侧提示词
        Label label = new Label("共享根目录:");
        label.getStyleClass().addAll(Styles.TEXT_BOLD);
        label.setMinWidth(Region.USE_PREF_SIZE); // 防止提示词被压缩

        // 中间路径 (会自动截断)
        sharedPathLabel = new Label(AppConfig.getInstance().getRootPath());
        sharedPathLabel.setStyle("-fx-font-family: 'monospace'; -fx-font-weight: bold;");
        sharedPathLabel.getStyleClass().add(Styles.ACCENT);

        // 设置截断逻辑
        sharedPathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS); // 中间省略号
        sharedPathLabel.setMaxWidth(Double.MAX_VALUE); // 允许它在空间足够时伸展

        // 关键：让路径标签占据所有剩余空间
        HBox.setHgrow(sharedPathLabel, Priority.ALWAYS);


        Button openBtn = new Button();

        // 创建 SVG 节点
        openBtn.setGraphic(createIcon(SVG_FOLDER,"-color-fg-default"));
        openBtn.getStyleClass().addAll(Styles.BUTTON_ICON); // 使用图标按钮 + 扁平样式
        openBtn.setTooltip(new Tooltip("在资源管理器中打开"));
        openBtn.setOnAction(e -> {
            try {
                String path = AppConfig.getInstance().getRootPath();
                java.awt.Desktop.getDesktop().open(new java.io.File(path));
            } catch (Exception ex) {
                MessageUtils.showToast("打开文件夹失败");
            }
        });
        openBtn.setMinWidth(Region.USE_PREF_SIZE);
        // 右侧按钮
        Button setSharedBtn = new Button("设为当前目录");
        setSharedBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        setSharedBtn.setMinWidth(Region.USE_PREF_SIZE); // 防止按钮被压缩
        setSharedBtn.setOnAction(e -> applyNewSharedDirectory(currentBrowsingDir));

        box.getChildren().addAll(label, sharedPathLabel, openBtn,setSharedBtn);
        return box;
    }

    private HBox createBrowseControlBar() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0));

        Label label = new Label("正在浏览:");
        label.getStyleClass().add(Styles.TEXT_MUTED);

        Button upBtn = new Button("↑");
        upBtn.setTooltip(new Tooltip("返回上一级"));
        upBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        upBtn.setPrefWidth(btnSize);
        upBtn.setOnAction(e -> navigateUp());


        Button refreshBtn = new Button();
        refreshBtn.setGraphic(createIcon(SVG_REFRESH, "-color-fg-default")); // 使用主题默认色
        refreshBtn.setTooltip(new Tooltip("刷新文件列表"));
        refreshBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        refreshBtn.setPrefWidth(btnSize);
        refreshBtn.setOnAction(e -> refreshFileList(currentBrowsingDir)); // 重新加载当前目录


        browsePathField = new TextField();
        browsePathField.setEditable(false);
        browsePathField.setStyle("-fx-font-family: 'monospace';");
        HBox.setHgrow(browsePathField, Priority.ALWAYS);

        Button chooseDirBtn = new Button("选择目录...");
        chooseDirBtn.getStyleClass().add(Styles.BUTTON_OUTLINED);
        chooseDirBtn.setOnAction(e -> handleChooseDirectory());

        historyBox = new ComboBox<>();
        historyBox.setPromptText("常用目录");
        historyBox.setPrefWidth(120);
        String savedHistory = AppConfig.getInstance().getLocalShareHistory();
        if (savedHistory != null && !savedHistory.isEmpty()) {
            String[] paths = savedHistory.split(";");
            historyBox.getItems().addAll(paths);
        }
        historyBox.setOnAction(e -> {
            if (historyBox.getValue() != null) {
                // 加个判断防止空指针
                File dir = new File(historyBox.getValue());
                if (dir.exists()) refreshFileList(dir);
            }
        });

        box.getChildren().addAll(label, upBtn, refreshBtn,browsePathField, chooseDirBtn, historyBox);
        return box;
    }

    private TableView<FileItem> createFileTable() {
        TableView<FileItem> table = new TableView<>();
        Label emptyLabel = new Label("文件夹为空");
        table.setPlaceholder(emptyLabel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add(Styles.STRIPED);
        table.getStyleClass().add(Styles.ELEVATED_1);
        table.setStyle("-fx-background-radius: 8;");

        TableColumn<FileItem, FileItem> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        nameCol.setCellFactory(col -> new TableCell<FileItem, FileItem>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName()); // 显示文件名

                    String svgContent = item.isDirectory() ? SVG_FOLDER : SVG_FILE;
                    String color = item.isDirectory() ? "#ffc107" : "#757575"; // 文件夹金色，文件灰色

                    setGraphic(createIcon(svgContent, color));
                }
            }
        });
        nameCol.setMinWidth(80);
        nameCol.setReorderable(false);

        TableColumn<FileItem, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());
        sizeCol.setPrefWidth(120);
        sizeCol.setMinWidth(80);
        sizeCol.setStyle("-fx-alignment: CENTER_RIGHT;");
        sizeCol.setReorderable(false);

        TableColumn<FileItem, String> dateCol = new TableColumn<>("修改日期");
        dateCol.setCellValueFactory(data -> data.getValue().dateProperty());
        dateCol.setPrefWidth(160);
        dateCol.setMinWidth(120);
        dateCol.setReorderable(false);
        table.getColumns().addAll(nameCol, sizeCol, dateCol);

        table.setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    FileItem item = row.getItem();
                    if (item.isDirectory()) {
                        refreshFileList(new File(item.getAbsolutePath()));
                    }
                }
            });
            return row;
        });

        return table;
    }

    /**
     * 底部控制栏 (带状态监听)
     */
    private HBox createBottomControls() {
        HBox box = new HBox(20);
        box.setAlignment(Pos.CENTER_RIGHT);
        SimpleToggleSwitch uploadSwitch = new SimpleToggleSwitch("允许写入 (上传/删除)");
        uploadSwitch.selectedProperty().bindBidirectional(AppConfig.getInstance().allowUploadProperty());
        uploadSwitch.selectedProperty().addListener((obs, oldVal, newVal) ->
                AppConfig.getInstance().setAllowUpload(newVal));

        ModeActionButton actionBtn = new ModeActionButton(ServerMode.LOCAL_SHARE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        box.getChildren().addAll(uploadSwitch, spacer, actionBtn);
        return box;
    }

    // ================= 逻辑处理 =================


    private void applyNewSharedDirectory(File dir) {
        if (dir == null || !dir.exists()) return;

        String path = dir.getAbsolutePath();

        // 1. 更新配置和顶部标签
        AppConfig.getInstance().setRootPath(path);
        sharedPathLabel.setText(path);

        // 2. 处理历史记录下拉框逻辑
        ObservableList<String> history = historyBox.getItems();
        if (history.contains(path)) {
            history.remove(path);
        } else if (history.size() >= 5) {
            history.remove(history.size() - 1);
        }
        history.add(0, path);
        historyBox.getSelectionModel().select(0);

        // 3. 持久化历史记录到配置文件
        String historyStr = String.join(";", history);
        AppConfig.getInstance().setLocalShareHistory(historyStr);

        // 4. 提示
        MessageUtils.showToast("共享目录已更新");
    }
    private void refreshFileList(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            File userHome = new File(System.getProperty("user.home"));
            if(!userHome.equals(currentBrowsingDir)) refreshFileList(userHome);
            return;
        }

        this.currentBrowsingDir = dir;
        browsePathField.setText(dir.getAbsolutePath());

        ObservableList<FileItem> data = FXCollections.observableArrayList();
        File[] files = dir.listFiles();

        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            for (File f : files) {
                if (f.isHidden() || !f.canRead()) continue;
                String sizeStr = f.isDirectory() ? "-" : formatSize(f.length());
                String dateStr = sdf.format(new Date(f.lastModified()));
                data.add(new FileItem(f.getName(), sizeStr, dateStr, f.isDirectory(), f.getAbsolutePath()));
            }
        }
        fileTable.setItems(data);
    }

    private void handleChooseDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择共享根目录");
        if (currentBrowsingDir != null && currentBrowsingDir.exists()) {
            dc.setInitialDirectory(currentBrowsingDir);
        }

        File selected = dc.showDialog(view.getScene().getWindow());
        if (selected != null) {
            // 1. 刷新下方正在浏览的列表，让用户看到选中的内容
            refreshFileList(selected);

            // 2. 【核心复u】直接应用刚才提取的共享设置逻辑
            applyNewSharedDirectory(selected);
        }
    }

    private void navigateUp() {
        if (currentBrowsingDir != null && currentBrowsingDir.getParentFile() != null) {
            refreshFileList(currentBrowsingDir.getParentFile());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private javafx.scene.shape.SVGPath createIcon(String content, String color) {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent(content);
        path.setStyle("-fx-fill: " + color + ";");
        return path;
    }
    public BorderPane getView() { return view; }
}