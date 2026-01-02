package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.core.service.SessionManager;
import com.vc6.model.UserSession;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class SessionsView {
    private final BorderPane view;

    public SessionsView() {
        this.view = new BorderPane();
        view.setPadding(new Insets(30));

        Label title = new Label("在线设备监控");
        title.getStyleClass().add(Styles.TITLE_3);
        view.setTop(title);

        TableView<UserSession> table = new TableView<>();
        table.setItems(SessionManager.getInstance().getSessionList());

        TableColumn<UserSession, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getUserId()));

        TableColumn<UserSession, String> nameCol = new TableColumn<>("用户");
        nameCol.setCellValueFactory(d -> d.getValue().nicknameProperty());
        
        TableColumn<UserSession, String> deviceCol = new TableColumn<>("型号");
        deviceCol.setCellValueFactory(d -> d.getValue().deviceNameProperty());

        TableColumn<UserSession, String> ipCol = new TableColumn<>("IP 地址");
        ipCol.setCellValueFactory(d -> d.getValue().ipProperty());

//        TableColumn<UserSession, Number> lastActiveCol = new TableColumn<>("上次活跃");
//        lastActiveCol.setCellValueFactory(d -> d.getValue().lastActiveProperty());
//        lastActiveCol.setCellFactory(column -> new TableCell<UserSession, Number>() {
//            private final java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
//            @Override
//            protected void updateItem(Number item, boolean empty) {
//                super.updateItem(item, empty);
//                if (empty || item == null) {
//                    setText(null);
//                } else {
//                    setText(sdf.format(new java.util.Date(item.longValue())));
//                }
//            }
//        });

        table.getColumns().addAll(idCol,nameCol, deviceCol, ipCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        view.setCenter(table);
        BorderPane.setMargin(table, new Insets(20, 0, 0, 0));
    }

    public BorderPane getView() { return view; }
}