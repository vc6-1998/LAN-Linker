package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.core.service.QuickShareService; // 引入新服务
import com.vc6.gui.component.ModeActionButton;
import com.vc6.model.ServerMode;
import com.vc6.utils.MessageUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class QuickShareView {

    private final BorderPane view;
    private final QuickShareService service; // 持有 Service
    private ListView<File> feedList;
    private Timer refreshTimer;

    public QuickShareView() {
        this.service = new QuickShareService(); // 初始化 Service
        this.view = new BorderPane();
        initView();
        startAutoRefresh();
    }

    private void initView() {
        view.setPadding(new Insets(30));

        // 1. 键盘监听 (Ctrl+V)
        view.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode().toString().equals("V")) {
                handlePaste();
                event.consume();
            }
        });
        view.setFocusTraversable(true);
        view.setOnMouseClicked(e -> view.requestFocus());

        // 2. 顶部发送区
        VBox topBox = new VBox(15);
        Label title = new Label("极速快传");
        title.getStyleClass().add(Styles.TITLE_3);
        topBox.getChildren().addAll(title, createSendArea());
        view.setTop(topBox);

        // 3. 中部列表
        feedList = new ListView<>();
        feedList.setCellFactory(param -> new FeedCell());
        feedList.getStyleClass().add(Styles.STRIPED);
//        feedList.setHorizontalScrollBarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(feedList, Priority.ALWAYS);

        setupDragAndDrop(view); // 绑定拖拽到整个视图
        Platform.runLater(() -> {refreshFeed();});
         // 初始加载

        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(20, 0, 20, 0));
        centerBox.getChildren().addAll(new Label("按 Ctrl+V 或 拖拽文件 可直接上传"), feedList);
        view.setCenter(centerBox);

        // 4. 底部按钮
        HBox bottomBox = new HBox();
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        bottomBox.getChildren().add(new ModeActionButton(ServerMode.QUICK_SHARE));
        view.setBottom(bottomBox);
    }

    private HBox createSendArea() {
        HBox box = new HBox(10);
        TextArea inputField = new TextArea();
        inputField.setPromptText("在此输入文本消息...");
        inputField.setPrefHeight(60);
        inputField.setWrapText(true);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        // --- 1. 定义统一的发送动作 ---
        Runnable doSend = () -> {
            String text = inputField.getText();
            if (text != null && !text.trim().isEmpty()) {

                service.saveText(text);
                inputField.clear();
                refreshFeed();
            }
        };

        // --- 2. 绑定 Ctrl+Enter 快捷键 ---
        inputField.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                doSend.run();
                e.consume(); // 阻止换行符被输入到文本框中
            }
        });

        // --- 3. 制作 Google AI Studio 风格按钮 ---
        Button sendBtn = new Button();
        sendBtn.setPrefHeight(60);
        sendBtn.setMinWidth(Region.USE_PREF_SIZE);
        sendBtn.getStyleClass().add(Styles.ACCENT); // 蓝色背景

        // 自定义按钮内容：左边“发送”，右边小字“Ctrl ↵”
        HBox btnContent = new HBox(8);
        btnContent.setAlignment(Pos.CENTER);

        Label mainText = new Label("发送");
        mainText.setStyle("-fx-text-fill: -color-fg-emphasis; -fx-font-weight: bold; -fx-font-size: 14px;");

        Label subText = new Label("Ctrl ↵");
        subText.setStyle("-fx-text-fill: -color-fg-emphasis; -fx-opacity: 0.7; -fx-font-size: 11px;");

        btnContent.getChildren().addAll(mainText, subText);
        sendBtn.setGraphic(btnContent);

        sendBtn.setOnAction(e -> doSend.run());

        box.getChildren().addAll(inputField, sendBtn);
        return box;
    }

    // --- 事件处理逻辑 (委托给 Service) ---

    private void handlePaste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            service.saveFiles(clipboard.getFiles());
            refreshFeed();
        } else if (clipboard.hasImage()) {
            service.saveImage(clipboard.getImage());
            refreshFeed();
        }else if (clipboard.hasString()) {
            service.saveText(clipboard.getString());
            refreshFeed();
        }
    }

    private void setupDragAndDrop(javafx.scene.Node node) {
        node.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        node.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (files != null) {
                service.saveFiles(files); // 调用 Service
                refreshFeed();
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private void refreshFeed() {
        List<File> files = service.getFeedList(); // 调用 Service
        Platform.runLater(() -> feedList.getItems().setAll(files));
    }

    private void startAutoRefresh() {
        refreshTimer = new Timer(true);
        refreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (view.getScene() != null) refreshFeed();
            }
        }, 1000, 2000);
    }


    public BorderPane getView() { return view; }


    private class FeedCell extends ListCell<File> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        private static final String SVG_COPY = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
        private static final String SVG_TRASH = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";

        @Override
        protected void updateItem(File item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null); setText(null); setStyle(""); setPadding(Insets.EMPTY);
            } else {
                HBox row = new HBox(12);
                row.setAlignment(Pos.TOP_LEFT);
                row.setPadding(new Insets(8, 10, 8, 15));
                row.setStyle("-fx-border-color: transparent transparent -color-border-subtle transparent;");

                // 1. 图标
                Label iconLabel = new Label();
                iconLabel.setStyle("-fx-font-size: 20px; -fx-padding: -2 0 0 0;"); // 微调垂直位置

                // 2. 中间内容区 (文本 + f下方的元数据行)
                VBox centerBox = new VBox(4); // 行间距
                HBox.setHgrow(centerBox, Priority.ALWAYS);

                Label mainText = new Label();
                mainText.setWrapText(true); // 开启换行

                // 【修改 1】允许宽度填满容器，这能帮助 Label 正确计算换行点
                mainText.setMaxWidth(Double.MAX_VALUE);

                // 【修改 2】强制最小高度跟随内容变化，防止被压扁
                mainText.setMinHeight(Region.USE_PREF_SIZE);

                // 【修改 3】放宽最大高度限制。85px 可能有点紧，给到 100px (约 4-5 行)
                mainText.setMaxHeight(100);
                mainText.setTextOverrun(OverrunStyle.ELLIPSIS); // 超出显示省略号...
                mainText.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-default;");

                // 元数据行 (时间 + 大小)
                HBox metaBox = new HBox(10);
                metaBox.setAlignment(Pos.CENTER_LEFT);

                // 【调整 3】时间移到左边
                Label timeLabel = new Label(sdf.format(new Date(item.lastModified())));
                timeLabel.getStyleClass().add(Styles.TEXT_SMALL);
                timeLabel.setStyle("-fx-text-fill: -color-fg-muted;");

                Label sizeLabel = new Label();
                sizeLabel.getStyleClass().add(Styles.TEXT_SMALL);
                sizeLabel.setStyle("-fx-text-fill: -color-fg-muted;");

                boolean isTextMsg = item.getName().endsWith(".lanmsg");
                if (isTextMsg) {
                    iconLabel.setText("💬");
                    String content = "";
                    try { content = Files.readString(item.toPath()); } catch (Exception e){}

                    String displayContent = content;

                    if (displayContent.length() > 200) {
                        displayContent = displayContent.substring(0, 200) + "...";
                    }

                    String[] lines = displayContent.split("\n");
                    if (lines.length > 5) {
                        displayContent = String.join("\n", Arrays.copyOf(lines, 5)) + "...";
                    }

                    mainText.setText(displayContent);
                    mainText.setWrapText(true);
                    mainText.setMaxWidth(Double.MAX_VALUE);
                    if (getListView() != null) {
                        mainText.prefWidthProperty().bind(getListView().widthProperty().subtract(240));
                    }
                    sizeLabel.setText(content.length() + " 字");
                } else {
                    iconLabel.setText("📄");
                    mainText.setText(item.getName());
                    sizeLabel.setText(formatSize(item.length()));

                }
                metaBox.getChildren().addAll(timeLabel, sizeLabel);
                centerBox.getChildren().addAll(mainText, metaBox);

                // 3. 右侧按钮区
                HBox buttons = new HBox(5);
                buttons.setAlignment(Pos.TOP_RIGHT);
                buttons.setMinWidth(Region.USE_PREF_SIZE);

                Button copyBtn = createIconBtn(SVG_COPY, isTextMsg ? "复制文本" : "复制文件");
                copyBtn.setOnAction(e -> {
                    ClipboardContent cc = new ClipboardContent();
                    if (isTextMsg) {
                        try { cc.putString(Files.readString(item.toPath())); } catch(Exception ex){}
                    } else {
                        cc.putFiles(List.of(item));
                    }
                    Clipboard.getSystemClipboard().setContent(cc);
                    MessageUtils.showToast("已复制 %s".formatted(isTextMsg ? "文本":item.getName()));
                });
                buttons.getChildren().add(copyBtn);

                Button delBtn = createIconBtn(SVG_TRASH, "删除");
                delBtn.getStyleClass().add(Styles.DANGER);
                delBtn.setOnAction(e -> {
                    service.deleteFile(item);
                    getListView().getItems().remove(item);
                    MessageUtils.showToast("已删除 %s".formatted(isTextMsg ? "文本":item.getName()));
                });
                buttons.getChildren().add(delBtn);

                row.getChildren().addAll(iconLabel, centerBox, buttons);
                setGraphic(row);
            }
        }

        private Button createIconBtn(String svg, String tooltip) {
            Button btn = new Button();
            btn.setTooltip(new Tooltip(tooltip));
            btn.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
            javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
            path.setContent(svg);
            path.getStyleClass().add("ikonli-font-icon");
            btn.setGraphic(path);
            return btn;
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }
    }
}
