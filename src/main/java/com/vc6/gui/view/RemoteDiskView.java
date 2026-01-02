package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.gui.component.ModeActionButton;
import com.vc6.gui.component.SimpleToggleSwitch;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.File;
public class RemoteDiskView {

    private final BorderPane view;
    private PasswordField pinField;
    private TableView<DriveInfo> driveTable;
    private static final String SVG_SHIELD = "M8.5 0.75a.75.75 0 0 1 .5 0l6.5 2.5a.75.75 0 0 1 .5.7V9a8 8 0 0 1-5.1 7.42l-2 0.82a.75.75 0 0 1-.6 0l-2-0.82A8 8 0 0 1 1 9V4a.75.75 0 0 1 .5-.7l6.5-2.5z";


    public RemoteDiskView() {
        this.view = new BorderPane();
        initView();
    }

    private void initView() {
        view.setPadding(new Insets(30));

        // 1. 顶部：标题 + 安全设置
        VBox topBox = new VBox(20);
        topBox.getChildren().addAll(createTitle(), createSecurityHintBox());
        view.setTop(topBox);

        // 2. 中部：磁盘列表预览
        VBox centerBox = new VBox(15);
        centerBox.setPadding(new Insets(20, 0, 20, 0));

        // 增加一个刷新按钮，方便查看磁盘变化（如插拔U盘）
        HBox labelBox = new HBox(10);
        labelBox.setAlignment(Pos.CENTER_LEFT);
        Label driveLabel = new Label("本机磁盘概览:");
        driveLabel.getStyleClass().add(Styles.TEXT_BOLD);
        Button refreshBtn = new Button("⟳"); // 简单刷新图标
        refreshBtn.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
        refreshBtn.setOnAction(e -> refreshDriveList());
        labelBox.getChildren().addAll(driveLabel, refreshBtn);

        driveTable = createDriveTable();
        refreshDriveList();

        centerBox.getChildren().addAll(labelBox, driveTable);
        view.setCenter(centerBox);

        // 3. 底部：控制栏 (新增了写入开关)
        HBox bottomBox = new HBox(20);
        bottomBox.setAlignment(Pos.CENTER_RIGHT);

        SimpleToggleSwitch writeSwitch = new SimpleToggleSwitch("允许写入 (上传/删除)");
        writeSwitch.selectedProperty().bindBidirectional(AppConfig.getInstance().allowUploadProperty());
        // 占位符，把按钮顶到最右边
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 启动按钮
        ModeActionButton actionBtn = new ModeActionButton(ServerMode.REMOTE_DISK);

        bottomBox.getChildren().addAll(writeSwitch, spacer, actionBtn);
        view.setBottom(bottomBox);
    }

    private HBox createTitle() {
        HBox box = new HBox();
        Label title = new Label("远程全盘访问");
        title.getStyleClass().add(Styles.TITLE_3);
        box.getChildren().add(title);
        return box;
    }

    /**
     * 安全设置卡片
     */
    private HBox createSecurityHintBox() {
        HBox box = new HBox(20); // 增加间距
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(15, 20, 15, 20));

        // 样式：深蓝色的警告背景，对齐 AtlantaFX 风格
        box.getStyleClass().addAll(Styles.ELEVATED_1, Styles.BG_INSET);
        box.setStyle("-fx-background-radius: 8; -fx-border-color: -color-accent-emphasis; -fx-border-radius: 8; -fx-border-width: 0.5;");

        // --- 使用 SVG 图标 ---
        javafx.scene.shape.SVGPath shield = new javafx.scene.shape.SVGPath();
        shield.setContent("M8.5 0.75a.75.75 0 0 1 .5 0l6.5 2.5a.75.75 0 0 1 .5.7V9a8 8 0 0 1-5.1 7.42l-2 0.82a.75.75 0 0 1-.6 0l-2-0.82A8 8 0 0 1 1 9V4a.75.75 0 0 1 .5-.7l6.5-2.5z");
        shield.setStyle("-fx-fill: -color-accent-fg;"); // 蓝色盾牌
        shield.setScaleX(1.8);
        shield.setScaleY(1.8);

        VBox textBox = new VBox(5);
        Label title = new Label("安全访问控制已接管");
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.ACCENT); // 蓝色粗体

        Label desc = new Label("本模式强制开启验证。请前往 [系统设置] 维护您的通用 PIN 码。");
        desc.getStyleClass().add(Styles.TEXT_SMALL);
        desc.setStyle("-fx-text-fill: -color-fg-muted;");

        textBox.getChildren().addAll(title, desc);

        box.getChildren().addAll(shield, textBox);
        return box;
    }
    /**
     * 磁盘列表表格
     */
    private TableView<DriveInfo> createDriveTable() {
        TableView<DriveInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().addAll(Styles.STRIPED, Styles.ELEVATED_1);
        table.setStyle("-fx-background-radius: 8;");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<DriveInfo, String> nameCol = new TableColumn<>("盘符");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path));
        nameCol.setReorderable(false);

        TableColumn<DriveInfo, String> totalCol = new TableColumn<>("总容量");
        totalCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().totalSpace));
        totalCol.setStyle("-fx-alignment: CENTER_RIGHT;");
        totalCol.setReorderable(false);

        TableColumn<DriveInfo, String> freeCol = new TableColumn<>("剩余空间");
        freeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().freeSpace));
        freeCol.setStyle("-fx-alignment: CENTER_RIGHT;");
        freeCol.setReorderable(false);

        table.getColumns().addAll(nameCol, freeCol, totalCol);
        return table;
    }

    private void refreshDriveList() {
        driveTable.getItems().clear();
        for (File root : File.listRoots()) {
            driveTable.getItems().add(new DriveInfo(root));
        }
    }

    // 简单的内部类用于表格展示
    public static class DriveInfo {
        String path;
        String totalSpace;
        String freeSpace;

        public DriveInfo(File root) {
            this.path = root.getPath();
            this.totalSpace = formatSize(root.getTotalSpace());
            this.freeSpace = formatSize(root.getFreeSpace());
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }
    }

    public BorderPane getView() { return view; }
}