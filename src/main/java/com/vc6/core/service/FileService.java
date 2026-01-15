package com.vc6.core.service;

import com.vc6.gui.component.LogPanel;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import com.vc6.model.UserSession;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URLEncoder;

public class FileService {

    private final java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();

    public void addHeader(String name, String value) {
        extraHeaders.put(name, value);
    }
    public File resolveFile(String uri) {
        ServerMode mode = AppConfig.getInstance().getServerMode();

        if (mode == ServerMode.LOCAL_SHARE) {
            String rootPath = AppConfig.getInstance().getRootPath();
            String relativePath = uri.startsWith("/") ? uri.substring(1) : uri;
            return relativePath.isEmpty() ? new File(rootPath) : new File(rootPath, relativePath);

        } else if (mode == ServerMode.REMOTE_DISK) {
            String absPath = uri.startsWith("/") ? uri.substring(1) : uri;
            if (absPath.matches("^[a-zA-Z]:?$")) {
                if (!absPath.contains(":")) absPath += ":";
                absPath += "/";
            }
            return new File(absPath);

        } else if (mode == ServerMode.QUICK_SHARE) {
            String rootPath = AppConfig.getInstance().getQuickSharePath();
            String relativePath = uri.startsWith("/") ? uri.substring(1) : uri;
            return relativePath.isEmpty() ? new File(rootPath) : new File(rootPath, relativePath);
        }

        return null;
    }

    public void handleUpload(ChannelHandlerContext ctx, FullHttpRequest req, String uri) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(true), req);
        try {
            if (decoder.isMultipart()) {
                File uploadDir = resolveFile(uri);
                if (!uploadDir.exists() || !uploadDir.isDirectory()) {
                    sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid Dir");
                    return;
                }
                while (decoder.hasNext()) {
                    InterfaceHttpData data = decoder.next();
                    if (data != null && data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                        FileUpload fileUpload = (FileUpload) data;
                        if (fileUpload.isCompleted()) {
                            String fileName = fileUpload.getFilename();

                            if (fileName == null || fileName.trim().isEmpty()) {
                                continue;
                            }

                            File dest = new File(uploadDir, fileName);
                            fileUpload.renameTo(dest);
                            LogPanel.log("[Service] "+getCurrentUserID(ctx) +": 文件上传成功: " + dest.getName());
                        }
                    }
                }
                sendRedirect(ctx, uri);
            }
        } catch (Exception e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            decoder.destroy();
        }
    }

    public void handleTextUpload(ChannelHandlerContext ctx, String text, String uri) {
        if (text == null || text.trim().isEmpty()) {
            sendRedirect(ctx, uri);
            return;
        }
        text = text.replace("\r\n", "\n");
        int maxLen = AppConfig.getInstance().getMaxTextLength();
        if (text.length() > maxLen) {
            // 如果超长，可以选择截断，或者报错
            // 这里演示报错
            sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Text too long (Max " + maxLen + " chars)");
            return;
        }
        try {
            String quickPath = AppConfig.getInstance().getQuickSharePath();
            File dir = new File(quickPath);
            if (!dir.exists()) dir.mkdirs();

            // 生成唯一文件名: clip_时间戳.lanmsg
            String filename = "clip_" + System.currentTimeMillis() + ".lanmsg";
            File dest = new File(dir, filename);

            java.nio.file.Files.writeString(dest.toPath(), text);
            LogPanel.log("[Service] "+getCurrentUserID(ctx) +": 上传文本消息: " + (text.length() > 10 ? text.substring(0, 10)+"..." : text));

            sendRedirect(ctx, uri);

        } catch (Exception e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Save Text Failed");
        }
    }

    public void downloadFile(ChannelHandlerContext ctx, File file, FullHttpRequest req) {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            long len = raf.length();
            HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpUtil.setContentLength(resp, len);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");

            String rawName = file.getName();
            String encodedName = URLEncoder.encode(rawName, "UTF-8").replace("+", "%20");
            resp.headers().set(HttpHeaderNames.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + rawName + "\"; filename*=UTF-8''" + encodedName);

            if (HttpUtil.isKeepAlive(req)) resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            extraHeaders.forEach((k, v) -> resp.headers().set(k, v));
            ctx.write(resp);
            ctx.write(new io.netty.handler.stream.ChunkedFile(raf, 0, len, 8192), ctx.newProgressivePromise());
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            LogPanel.log("[Service] "+getCurrentUserID(ctx) +": 访问文件: " + file.getName());

        } catch (Exception e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Download error");
        }
    }

    public void sendHtml(ChannelHandlerContext ctx, String content) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        extraHeaders.forEach((k, v) -> response.headers().set(k, v));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void sendRedirect(ChannelHandlerContext ctx, String newUrl) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        // 这里记得加上你之前的 URLEncoder 修复逻辑
        try {
            String[] parts = newUrl.split("/");
            StringBuilder encodedUrl = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) encodedUrl.append("/").append(URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
            }
            if (newUrl.endsWith("/")) encodedUrl.append("/");
            if (encodedUrl.length() == 0) encodedUrl.append("/");
            response.headers().set(HttpHeaderNames.LOCATION, encodedUrl.toString());
        } catch (Exception e) {
            response.headers().set(HttpHeaderNames.LOCATION, newUrl);
        }
        extraHeaders.forEach((k, v) -> response.headers().set(k, v));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String msg) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Error: " + status + " " + msg + "\r\n", CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        extraHeaders.forEach((k, v) -> response.headers().set(k, v));
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void serveStatic(ChannelHandlerContext ctx, String rawUri) {
        // 1. 映射路径：/static/xxx -> resources/static/xxx
        // 注意：getResourceAsStream 需要以 / 开头，代表从 classpath 根目录找
        String uri = rawUri.contains("?") ? rawUri.split("\\?")[0] : rawUri;
        try (java.io.InputStream is = getClass().getResourceAsStream(uri)) {
            if (is == null) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND, "Static resource not found");
                return;
            }

            byte[] bytes = is.readAllBytes();
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, io.netty.buffer.Unpooled.wrappedBuffer(bytes));

            // 2. 设置正确的 Content-Type (非常重要，否则样式无效)
            String mimeType = "application/octet-stream";
            if (uri.endsWith(".css")) mimeType = "text/css";
            else if (uri.endsWith(".js")) mimeType = "application/javascript";
            else if (uri.endsWith(".woff")) mimeType = "font/woff";
            else if (uri.endsWith(".woff2")) mimeType = "font/woff2";
            else if (uri.endsWith(".png")) mimeType = "image/png";

            response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeType);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

            extraHeaders.forEach((k, v) -> response.headers().set(k, v));
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Static load error");
        }
    }
    public void handleMkdir(ChannelHandlerContext ctx, String baseUri, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            sendRedirect(ctx, baseUri);
            return;
        }

        File currentDir = resolveFile(baseUri);
        if (currentDir != null && currentDir.exists() && currentDir.isDirectory()) {
            File newDir = new File(currentDir, folderName);
            if (!newDir.exists()) {
                boolean success = newDir.mkdir();
                LogPanel.log("[Service] "+getCurrentUserID(ctx) +(success ? ": 新建文件夹: " + newDir.getName() : ": 新建文件夹失败"));
            }
        }
        sendRedirect(ctx, baseUri);
    }
    public void handleDelete(ChannelHandlerContext ctx, String uri) {
        File file = resolveFile(uri);
        // 调用递归删除
        if (file.exists() && deleteRecursive(file)) {

            if (AppConfig.getInstance().getServerMode() == ServerMode.QUICK_SHARE) {
                sendRedirect(ctx, "/");
                return;
            }

            String parentUri = uri.substring(0, uri.lastIndexOf('/'));
            if (parentUri.isEmpty()) parentUri = "/";
            if (parentUri.matches("^/[a-zA-Z]:$")) parentUri += "/";

            sendRedirect(ctx, parentUri);
            LogPanel.log("[Service] "+getCurrentUserID(ctx) +": 文件已删除: " + file.getName());
        } else {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Delete failed");
        }
    }
    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    boolean success = deleteRecursive(child);
                    if (!success) return false; // 如果子文件删不掉，整体失败
                }
            }
        }
        return file.delete(); // 最后删除空文件夹自己
    }

    private String getCurrentUserID(ChannelHandlerContext ctx) {
        if (ctx == null) return "Administrator";

        UserSession user = ctx.channel().attr(AuthService.SESSION_KEY).get();
        if (user == null) return "Unknown";


        boolean isSecure = AppConfig.getInstance().isGlobalAuthEnabled() ||
                AppConfig.getInstance().getServerMode() == ServerMode.REMOTE_DISK;

        if (!isSecure) {
            // If security is off, maybe user prefers "Anonymous"
            return user.getIp();
        }

        return user.getUserId();
    }

}


