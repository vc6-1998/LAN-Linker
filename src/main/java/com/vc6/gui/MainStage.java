package com.vc6.gui;

import com.vc6.gui.component.Sidebar;
import com.vc6.utils.MessageUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainStage {

    private final BorderPane rootLayout;
    private final Scene scene;

    public MainStage(Stage primaryStage) {
        primaryStage.initStyle(StageStyle.DECORATED);

        rootLayout = new BorderPane();

        Sidebar sidebar = new Sidebar(this);
        rootLayout.setLeft(sidebar.getView());

        scene = new Scene(rootLayout, 1000, 740);

        primaryStage.setTitle("LAN-Linker Server");
        try {
            Image icon = new javafx.scene.image.Image(getClass().getResourceAsStream("/icon.png"));
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("窗口图标加载失败");
        }
        primaryStage.setScene(scene);

        MessageUtils.init(primaryStage);

        primaryStage.show();
    }

    public void switchView(javafx.scene.Node view) {
        rootLayout.setCenter(view);
    }

    public Scene getScene() {
        return scene;
    }
}