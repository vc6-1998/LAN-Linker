package com.vc6.gui.component;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogPanel {
    private static TextArea logArea;
    private static final StringBuilder buffer = new StringBuilder();
    private static final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");

    // 日志保存目录
    private static final String LOG_DIR = "logs";

    public static void setLogArea(TextArea area) {
        logArea = area;
        if (logArea != null) {
            final String content;
            synchronized (buffer) {
                content = buffer.toString();
            }
            Platform.runLater(() -> {
                logArea.setText(content);
                logArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }

    public static void log(String msg) {
        String time = timeFmt.format(new Date());
        String finalMsg = String.format("[%s] %s\n", time, msg);

        // 1. 存入内存 Buffer
        synchronized (buffer) {
            buffer.append(finalMsg);
        }

        // 2. 【核心新增】异步写入本地文件
        // 放在新线程里写，防止磁盘 IO 阻塞网络或 UI
        new Thread(() -> writeToFile(finalMsg)).start();

        // 3. 更新 UI
        if (logArea != null) {
            Platform.runLater(() -> logArea.appendText(finalMsg));
        }
    }

    /**
     * 【新增】将日志写入硬盘文件
     */
    private static void writeToFile(String text) {
        try {
            // 1. 确保日志目录存在
            File dir = new File(LOG_DIR);
            if (!dir.exists()) dir.mkdirs();

            // 2. 生成按天命名的文件名: logs/log_2024-01-15.txt
            String fileName = LOG_DIR + File.separator + "log_" + dateFmt.format(new Date()) + ".txt";
            File file = new File(fileName);

            // 3. 以追加模式 (append: true) 写入内容
            // 使用 UTF-8 编码，防止中文乱码
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                out.print(text);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("无法写入日志文件: " + e.getMessage());
        }
    }

    public static void clearBuffer() {
        synchronized (buffer) {
            buffer.setLength(0);
        }
    }

    public static void redirectSystemOutputs() {
        // 重定向标准输出 (System.out)
        PrintStream outStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {} // 不用这个
            @Override
            public void write(byte[] b, int off, int len) {
                String msg = new String(b, off, len, StandardCharsets.UTF_8);
                if (!msg.trim().isEmpty()) log("[STDOUT] " + msg.trim());
            }
        });
        System.setOut(outStream);

        // 重定向标准错误 (System.err) - 这是抓 Bug 的关键！
        PrintStream errStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {}
            @Override
            public void write(byte[] b, int off, int len) {
                String msg = new String(b, off, len, StandardCharsets.UTF_8);
                if (!msg.trim().isEmpty()) log("[STDERR] " + msg.trim());
            }
        });
        System.setErr(errStream);
    }
}