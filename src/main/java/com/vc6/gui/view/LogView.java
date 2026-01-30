package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.gui.component.LogPanel;
import com.vc6.utils.MessageUtils;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class LogView {

    private final BorderPane view;
    private TextArea logArea;

    public LogView() {
        this.view = new BorderPane();
        initView();
    }

    private void initView() {
        view.setPadding(new Insets(30));

        // 顶部工具栏
        HBox topBar = new HBox(15);
        Label title = new Label("系统运行日志");
        title.getStyleClass().add(Styles.TITLE_3);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openLogBtn = new Button("打开日志文件夹");
        openLogBtn.setOnAction(e -> {
            try {
                File logDir = new File("logs");
                if (!logDir.exists()) logDir.mkdirs();
                java.awt.Desktop.getDesktop().open(logDir);
            } catch (Exception ex) {
                MessageUtils.showToast("打开失败");
            }
        });

        Button clearBtn = new Button("清空日志");
        clearBtn.setOnAction(e -> {
            logArea.clear();
            LogPanel.clearBuffer(); // 假设 LogPanel 提供了清理 buffer 的方法
        });

        Button exportBtn = new Button("导出...");
        exportBtn.setOnAction(e -> handleExport());

        topBar.getChildren().addAll(title, spacer, openLogBtn,clearBtn, exportBtn);
        view.setTop(topBar);

        // 中间日志框
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(20, 0, 0, 0));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setFont(Font.font("Consolas", 12));
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // 【关键】注册到 LogPanel
        LogPanel.setLogArea(logArea);

        centerBox.getChildren().add(logArea);
        view.setCenter(centerBox);
    }

    private void handleExport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("保存日志");
        fc.setInitialFileName("lanlinker.log");
        File file = fc.showSaveDialog(view.getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), logArea.getText());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public BorderPane getView() { return view; }
}