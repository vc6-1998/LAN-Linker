package com.vc6.core.handler;

import com.vc6.core.service.AuthService;
import com.vc6.core.service.FileService;
import com.vc6.core.service.HtmlGenerator;
import com.vc6.gui.component.LogPanel;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import com.vc6.model.UserSession;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import javafx.application.Platform;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final FileService fileService = new FileService();
    private final AuthService authService = new AuthService();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        String rawUri = req.uri();
        String uriPath = new QueryStringDecoder(rawUri).path();
        String decodedUri = URLDecoder.decode(uriPath, StandardCharsets.UTF_8);


        // 1. 静态资源拦截
        if (rawUri.startsWith("/static/")) {
            fileService.serveStatic(ctx, rawUri);
            return;
        }

        // 2. 身份识别：为每个请求分配/识别 UID
        DefaultFullHttpResponse tempResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        UserSession user = authService.identifyUser(ctx, req, tempResp);
        ctx.channel().attr(AuthService.SESSION_KEY).set(user);

        if (tempResp.headers().contains(HttpHeaderNames.SET_COOKIE)) {
            String cookie = tempResp.headers().get(HttpHeaderNames.SET_COOKIE);
            fileService.addHeader(HttpHeaderNames.SET_COOKIE.toString(), cookie);
        }

        // 3. 处理登录请求
        if ("/login".equals(decodedUri)) {
            if (req.method() == HttpMethod.POST) {
                handleLogin(ctx, req, user);
            } else {
                fileService.sendHtml(ctx, HtmlGenerator.generateLoginPage(null, user.getNickname()));
            }
            return;
        }

        // 4. 安全拦截逻辑
        boolean isGlobalAuth = AppConfig.getInstance().isGlobalAuthEnabled();
        ServerMode mode = AppConfig.getInstance().getServerMode();
        boolean needsAuth = isGlobalAuth || (mode == ServerMode.REMOTE_DISK);

        if (needsAuth && !authService.isConfiguredAndLoggedIn(req)) {
            LogPanel.log("[Security] "+getCurrentUserID(ctx)+": 拦截未授权访问: " + decodedUri);
            fileService.sendHtml(ctx, HtmlGenerator.generateLoginPage(null, user.getNickname()));
            return;
        }

        // 5. 日志与 Favicon 过滤
        if ("/favicon.ico".equals(uriPath)) {
            fileService.sendError(ctx, HttpResponseStatus.NOT_FOUND, "");
            return;
        }
        if (AppConfig.getInstance().isDebugMode()) {
            LogPanel.log("[Req] " + getCurrentUserID(ctx) + ": " + req.method() + " " + rawUri);
        }
        // 6. 核心业务处理
        processFileRequest(ctx, decodedUri, req,user);
    }

    private void processFileRequest(ChannelHandlerContext ctx, String uri, FullHttpRequest req, UserSession user) {
        ServerMode mode = AppConfig.getInstance().getServerMode();

        String nickname = user.getNickname();

        // A. 处理 POST 数据
        if (req.method() == HttpMethod.POST) {
            if (!AppConfig.getInstance().isAllowUpload()) {
                fileService.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Write Denied");
                return;
            }
            if ("/api/text".equals(uri)) {
                handleTextPost(ctx, req);
            } else {
                fileService.handleUpload(ctx, req, uri);
            }
            return;
        }

        // B. 处理 API 指令 (删除)
        QueryStringDecoder qsd = new QueryStringDecoder(req.uri());
        if (qsd.parameters().containsKey("action")) {
            if (!AppConfig.getInstance().isAllowUpload()) {
                fileService.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Write Denied");
                return;
            }
            String action = qsd.parameters().get("action").get(0);
            if ("delete".equals(action)) {
                fileService.handleDelete(ctx, uri);
                return;
            }

            // 【新增：新建文件夹】
            if ("mkdir".equals(action)) {
                String name = "";
                if (qsd.parameters().containsKey("name")) {
                    name = qsd.parameters().get("name").get(0);
                }
                // 解码文件夹名 (防止中文乱码)
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);
                fileService.handleMkdir(ctx, uri, name);
                return;
            }

        }

        // C. 处理页面渲染
        // 1. 远程模式根目录
        if (mode == ServerMode.REMOTE_DISK && "/".equals(uri)) {
            fileService.sendHtml(ctx, HtmlGenerator.generateDriveList(nickname));
            return;
        }
        // 2. 快传模式根目录
        if (mode == ServerMode.QUICK_SHARE && "/".equals(uri)) {
            File root = fileService.resolveFile("/");
            if (!root.exists()) root.mkdirs();
            fileService.sendHtml(ctx, HtmlGenerator.generateQuickSharePage(root, nickname));
            return;
        }

        // 3. 通用浏览与下载
        File file = fileService.resolveFile(uri);
        if (file == null || !file.exists()) {
            fileService.sendRedirect(ctx, "/");
            return;
        }

        if (file.isDirectory()) {
            fileService.sendHtml(ctx, HtmlGenerator.generateFileList(file, uri, nickname));
        } else {
            fileService.downloadFile(ctx, file, req);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, FullHttpRequest req, UserSession user) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), req);
        try {
            String inputPin = "";
            String inputNickname = "";
            try {
                while (decoder.hasNext()) {
                    InterfaceHttpData data = decoder.next();
                    if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                        Attribute attr = (Attribute) data;
                        if ("pin".equals(attr.getName())) inputPin = attr.getValue();
                        else if ("nickname".equals(attr.getName())) inputNickname = attr.getValue();
                    }
                }
            } catch (Exception ignored) {}

            if (authService.verifyPin(inputPin)) {
                LogPanel.log("[Auth] "+getCurrentUserID(ctx)+": 登录成功 (Device: " + user.getDeviceName() + ")");

                // 更新昵称
                if (!inputNickname.isEmpty()) {
                    final String nick = inputNickname;
                    Platform.runLater(() -> user.setNickname(nick));
                }

                // 下发 Cookie
                FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
                resp.headers().set(HttpHeaderNames.LOCATION, "/");
                resp.headers().add(HttpHeaderNames.SET_COOKIE, authService.createAuthCookie());

                ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
            } else {
                fileService.sendHtml(ctx, HtmlGenerator.generateLoginPage("PIN 码错误", user.getNickname()));
                LogPanel.log("[Auth] "+user.getIp()+": 登录失败 (Device: " + user.getDeviceName() + ")");

            }
        } catch (Exception e) {
            fileService.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Login Error");
        } finally {
            decoder.destroy();
        }
    }

    private void handleTextPost(ChannelHandlerContext ctx, FullHttpRequest req) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), req);
        String content = "";
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute attr = (Attribute) data;
                    if ("content".equals(attr.getName())) content = attr.getValue();
                }
            }
        } catch (Exception ignored) {} finally { decoder.destroy(); }
        fileService.handleTextUpload(ctx, content, "/");
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