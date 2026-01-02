package com.vc6.core.service;

import com.vc6.model.AppConfig;
import com.vc6.utils.MessageUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;

public class QuickShareService {


    public QuickShareService() {
    }

    /**
     * 保存文本消息
     */
    public void saveText(String text) {
        if (text == null) return;
        String cleanText = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        if (cleanText.isEmpty()) return;
        int limit = AppConfig.getInstance().getMaxTextLength();
        if (cleanText.length() > limit) {
            MessageUtils.showError("文本过长", "超出字数限制 (%d字)".formatted(limit));
            return;
        }
        try {
            File dir = new File(AppConfig.getInstance().getQuickSharePath());
            if (!dir.exists()) dir.mkdirs();
            String filename = "clip_" + System.currentTimeMillis() + ".lanmsg";
            Files.writeString(new File(dir, filename).toPath(), cleanText);
            MessageUtils.showToast("已上传文本");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存文件列表 (来自拖拽或粘贴)
     */
    public void saveFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        long maxBytes = AppConfig.getInstance().getMaxFileSizeMb() * 1024 * 1024;
        int valid_cnt=0;
        for (File f : files) {
            try {
                if (f.isFile()) {
                    if (f.length() > maxBytes) {
                        MessageUtils.showError("文件过大", f.getName() + " 超出文件大小限制 (" + AppConfig.getInstance().getMaxFileSizeMb() + "MB)");
                        continue;
                    }
                    valid_cnt++;
                    File dest = new File(AppConfig.getInstance().getQuickSharePath(), f.getName());
                    Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    dest.setLastModified(System.currentTimeMillis());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        MessageUtils.showToast("已上传 %d 个文件".formatted(valid_cnt));
    }

    public void saveImage(javafx.scene.image.Image image) {
        if (image == null) return;
        try {
            // 确保目录存在
            File dir = new File(AppConfig.getInstance().getQuickSharePath());
            if (!dir.exists()) dir.mkdirs();

            // 生成文件名
            String filename = "picture_" + System.currentTimeMillis() + ".png";
            File dest = new File(dir, filename);


            java.awt.image.BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
            ImageIO.write(bImage, "png", dest);

            // 修正时间戳，确保排在最前
            dest.setLastModified(System.currentTimeMillis());
            MessageUtils.showToast("已上传图片");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取文件列表 (已排序)
     */
    public List<File> getFeedList() {
        File dir = new File(AppConfig.getInstance().getQuickSharePath());
        if (!dir.exists()) return List.of();

        File[] files = dir.listFiles();
        if (files == null) return List.of();

        // 按时间倒序
        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        return Arrays.asList(files);
    }

    /**
     * 删除文件
     */
    public void deleteFile(File file) {
        if (file != null) file.delete();
    }
}