package com.vc6.gui.view;

import atlantafx.base.theme.Styles;
import com.vc6.core.service.SessionManager;
import com.vc6.gui.component.SimpleToggleSwitch;
import com.vc6.model.AppConfig;
import com.vc6.model.UserSession;
import com.vc6.utils.MessageUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SessionsView {
    private final BorderPane view;

    public SessionsView() {
        this.view = new BorderPane();
        view.setPadding(new Insets(30));

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("访问设备记录");
        title.getStyleClass().add(Styles.TITLE_3);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        AppConfig config = AppConfig.getInstance();

        javafx.collections.transformation.FilteredList<UserSession> filteredData =
                new javafx.collections.transformation.FilteredList<>(SessionManager.getInstance().getSessionList(), s -> true);


        SimpleToggleSwitch showAllCheck = new SimpleToggleSwitch("显示所有连接 (包括未认证访客)");
        filteredData.predicateProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(() ->
                        user -> showAllCheck.isSelected() || user.isValuable(),
                showAllCheck.selectedProperty()
        ));

        config.globalAuthEnabledProperty().addListener((obs, old, isAuthEnabled) -> {
            showAllCheck.setSelected(!isAuthEnabled);
        });
        showAllCheck.setSelected(!config.isGlobalAuthEnabled());

        header.getChildren().addAll(title, spacer, showAllCheck);
        view.setTop(header);

        TableView<UserSession> table = new TableView<>();
        Label emptyLabel = new Label("访问记录为空");
        table.setPlaceholder(emptyLabel);
        table.setSelectionModel(null);
        table.setItems(filteredData);

        table.setRowFactory(tv -> {
            TableRow<UserSession> row = new TableRow<>();
            javafx.beans.value.ChangeListener<Boolean> valuableChangeListener = (obs, oldVal, newVal) -> {
                if (row.getItem() != null) {
                    row.setOpacity(newVal ? 1.0 : 0.6);
                }
            };

            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (oldItem != null) {
                    oldItem.valuableProperty().removeListener(valuableChangeListener);
                }

                if (newItem != null) {
                    newItem.valuableProperty().addListener(valuableChangeListener);
                    row.setOpacity(newItem.isValuable() ? 1.0 : 0.6);
                } else {
                    row.setOpacity(1.0);
                }
            });

            return row;
        });

        TableColumn<UserSession, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getUserId()));
        idCol.setMinWidth(110);
        idCol.setMaxWidth(110);
        idCol.setReorderable(false);

        TableColumn<UserSession, Boolean> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(d -> d.getValue().valuableProperty());
        statusCol.setReorderable(false);
        statusCol.setCellFactory(col -> new TableCell<UserSession, Boolean>() {
            @Override
            protected void updateItem(Boolean isValuable, boolean empty) {
                super.updateItem(isValuable, empty);
                if (empty || isValuable == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (isValuable) {
                        setText("🟢 已授权");
                        setStyle("-fx-text-fill: -color-success-fg; -fx-font-weight: bold;");
                    } else {
                        setText("⚪ 访客");
                        setStyle("-fx-text-fill: -color-fg-muted;");
                    }
                }
            }
        });
        statusCol.setMinWidth(100);
        statusCol.setMaxWidth(100);

        TableColumn<UserSession, String> nameCol = new TableColumn<>("用户名");
        nameCol.setCellValueFactory(d -> d.getValue().nicknameProperty());
        nameCol.setMinWidth(80);
        nameCol.setReorderable(false);
        TableColumn<UserSession, String> deviceCol = new TableColumn<>("型号");
        deviceCol.setCellValueFactory(d -> d.getValue().deviceNameProperty());
        deviceCol.setMinWidth(100);
        deviceCol.setMaxWidth(100);
        deviceCol.setReorderable(false);
        TableColumn<UserSession, String> ipCol = new TableColumn<>("IP 地址");
        ipCol.setCellValueFactory(d -> d.getValue().ipProperty());
        ipCol.setMinWidth(90);
        ipCol.setReorderable(false);
        TableColumn<UserSession, Number> timeCol = new TableColumn<>("上次活跃");
        timeCol.setMinWidth(100);
        timeCol.setReorderable(false);
        timeCol.setCellValueFactory(d -> d.getValue().lastActiveProperty());
        timeCol.setCellFactory(col -> new TableCell<UserSession, Number>() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss");
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(sdf.format(new Date(item.longValue())));
            }
        });


        // 【新增】操作列 (踢出按钮)
        TableColumn<UserSession, Void> actionCol = new TableColumn<>("");
        actionCol.setMinWidth(70);
        actionCol.setMaxWidth(70);
        actionCol.setReorderable(false);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("踢出");
            {
                btn.getStyleClass().addAll(Styles.SMALL, Styles.DANGER, Styles.BUTTON_OUTLINED);
                btn.setFocusTraversable(false);
                btn.setOnAction(e -> {
                    UserSession s = getTableView().getItems().get(getIndex());
                    if (MessageUtils.showConfirm("确认踢出", "确定要移除用户 " + s.getNickname() + " 吗？\n如果开启了安全验证，他将需要重新登录。")) {
                        SessionManager.getInstance().removeSession(s);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
                setAlignment(Pos.CENTER);
            }
        });

        table.getColumns().addAll(idCol, statusCol,nameCol, ipCol,deviceCol, timeCol, actionCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);


        view.setCenter(table);
        BorderPane.setMargin(table, new Insets(20, 0, 0, 0));
    }

    public BorderPane getView() { return view; }
}