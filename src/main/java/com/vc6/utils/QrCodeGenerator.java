package com.vc6.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vc6.model.AppConfig;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public class QrCodeGenerator {

    /**
     * 生成二维码图片
     *
     * @param content 内容 (如 http://192.168.1.5:8080)
     * @param size    宽度和高度 (像素)
     */
    public static javafx.scene.image.Image generate(String content, int size) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                    content, BarcodeFormat.QR_CODE, size, size
            );

            WritableImage image = new WritableImage(size, size);
            PixelWriter writer = image.getPixelWriter();

            // 1. 获取当前主题模式
            boolean isDark = AppConfig.getInstance().isDarkMode();

            // 2. 定义颜色
            // 深色模式：前景白色(White)，背景透明(或深灰)
            // 浅色模式：前景黑色(Black)，背景透明(或白色)
            // 注意：ARGB 格式 (Alpha, Red, Green, Blue)
            int foreColor = isDark ? 0xFFFFFFFF : 0xFF000000;
            int backColor = 0x00000000; // 透明背景，这样最好看，能透出卡片底色

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    // 矩阵里的 true 代表“有内容”(前景)，false 代表“空白”(背景)
                    writer.setArgb(x, y, bitMatrix.get(x, y) ? foreColor : backColor);
                }
            }
            return image;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}