package com.vc6.gui.component;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SimpleToggleSwitch extends HBox {

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Rectangle background;
    private final Circle circle;
    private final TranslateTransition transition;

    // 参数配置 (微调这三个数即可改变大小)
    private final double width = 40;
    private final double height = 20;
    private final double radius = 10;
    private final double moveDistance = width - (radius * 2); // 圆点移动的总距离

    public SimpleToggleSwitch(String text) {
        this.setAlignment(Pos.CENTER_LEFT);
        this.setSpacing(10);

        Label label = new Label(text);
        label.getStyleClass().add("label"); // 确保跟随主题变色

        // 背景
        background = new Rectangle(width, height);
        background.setArcWidth(height);
        background.setArcHeight(height);

        // 圆点
        circle = new Circle(radius - 3); // 稍微小一点，留出边距
        circle.setFill(Color.WHITE);
        circle.setStroke(Color.valueOf("#d0d0d0"));

        StackPane switchPane = new StackPane();
        switchPane.getChildren().addAll(background, circle);
        switchPane.setAlignment(Pos.CENTER_LEFT); // 关键：左对齐
        switchPane.setOnMouseClicked(e -> setSelected(!isSelected()));
        switchPane.setCursor(javafx.scene.Cursor.HAND);

        // 动画对象
        transition = new TranslateTransition(Duration.millis(200), circle);

        // 监听器
        selected.addListener((obs, oldVal, newVal) -> updateState(newVal));

        this.getChildren().addAll(switchPane, label);

        // 【关键修复】初始化时，直接根据当前值设置状态，不要硬编码 false
        updateState(isSelected());
    }

    private void updateState(boolean isOn) {
        // 【关键修复】先停止之前的动画，防止冲突
        transition.stop();

        if (isOn) {
            background.setFill(Color.valueOf("#28a745")); // 绿
            background.setStroke(Color.valueOf("#28a745"));

            // 目标位置：向右移动
            transition.setToX(3+moveDistance);
        } else {
            // 适配深色模式：暗灰色背景
            background.setFill(Color.valueOf("#6c757d"));
            background.setStroke(Color.valueOf("#6c757d"));

            // 目标位置：回到原点
            transition.setToX(3);
        }

        transition.play();
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean isSelected) { this.selected.set(isSelected); }
}