package com.vc6.gui.component;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogPanel {
    private static TextArea logArea;
    private static final StringBuilder buffer = new StringBuilder();
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    /**
     * 注册 UI 控件 (只在 DashboardView 创建时调用一次)
     */
    public static void setLogArea(TextArea area) {
        logArea = area;

        // 如果注册时已经有历史日志了，立刻回填
        if (logArea != null) {
            final String content;
            synchronized (buffer) {
                content = buffer.toString();
            }
            // 确保在 UI 线程执行
            Platform.runLater(() -> {
                logArea.setText(content);
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }

    /**
     * 打印日志 (核心修改在这里)
     */
    public static void log(String msg) {
        String time = sdf.format(new Date());
        String finalMsg = String.format("[%s] %s\n", time, msg);

        // 1. 存入内存 Buffer
        synchronized (buffer) {
            buffer.append(finalMsg);
        }

        // (可选) 控制台调试
        System.out.print("[DEBUG] " + finalMsg);

        // 2. 【关键】如果 TextArea 已经被注册了，就主动追加内容
        // 这样即使是缓存模式，新日志也能实时显示
        if (logArea != null) {
            Platform.runLater(() -> {
                logArea.appendText(finalMsg);
            });
        }
    }
    public static void clearBuffer() { synchronized(buffer) { buffer.setLength(0); } }
}