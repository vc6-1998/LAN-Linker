package com.vc6.core.handler;

import com.vc6.core.service.AuthService;
import com.vc6.core.service.FileService;
import com.vc6.core.service.HtmlGenerator;
import com.vc6.core.service.SessionManager;
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

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final FileService fileService = new FileService();
    private final AuthService authService = new AuthService();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String rawUri = req.uri();
        String decodedUri = new QueryStringDecoder(rawUri).path();

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
        if ("/login".equals(rawUri)) {
            if (req.method() == HttpMethod.POST) {
                handleLogin(ctx, req);
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
            LogPanel.log("[Auth] "+getCurrentUserID(ctx)+": 拦截未授权访问: " + decodedUri);

            if (user.isValuable()) {
                LogPanel.log("[Auth] 用户凭证失效，已强制下线: " + user.getUserId());
                user.setValuable(false);
                SessionManager.getInstance().removeSession(user);
            }
            fileService.sendHtml(ctx, HtmlGenerator.generateLoginPage(null, user.getNickname()));
            return;
        }

        // 5. 日志与 Favicon 过滤
        if ("/favicon.ico".equals(rawUri)) {
            fileService.sendError(ctx, HttpResponseStatus.NOT_FOUND, "");
            return;
        }
        if (AppConfig.getInstance().isDebugMode()) {
            LogPanel.log("[Req] " + getCurrentUserID(ctx) + ": " + req.method() + " " + rawUri);
        }
        // 6. 核心业务处理
        processFileRequest(ctx, req);
    }

    private void processFileRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        // 处理 POST 数据
        QueryStringDecoder qsd = new QueryStringDecoder(req.uri());
        String uri = qsd.path();

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

        //处理 API 指令 (删除)
        if (qsd.parameters().containsKey("action")) {
            if (!AppConfig.getInstance().isAllowUpload()) {
                fileService.sendError(ctx, HttpResponseStatus.FORBIDDEN, "Write Denied");
                return;
            }
            String action = qsd.parameters().get("action").getFirst();
            if ("delete".equals(action)) {
                fileService.handleDelete(ctx, uri);
                return;
            }

            // 【新增：新建文件夹】
            if ("mkdir".equals(action)) {
                String name = "";
                if (qsd.parameters().containsKey("name")) {
                    name = qsd.parameters().get("name").getFirst();
                }
                // 解码文件夹名 (防止中文乱码)
                fileService.handleMkdir(ctx, uri, name);
                return;
            }

        }

        ServerMode mode = AppConfig.getInstance().getServerMode();
        UserSession user = ctx.channel().attr(AuthService.SESSION_KEY).get();

        String nickname = AppConfig.getInstance().isGlobalAuthEnabled()? user.getNickname(): "Guest";

        //处理特殊根目录
        if ("/".equals(uri)) {
            if (mode == ServerMode.QUICK_SHARE) {
                File root = fileService.resolveFile("/");
                if (!root.exists()) root.mkdirs();
                fileService.sendHtml(ctx, HtmlGenerator.generateQuickSharePage(root, nickname));
                return;
            }
            if (mode == ServerMode.REMOTE_DISK) {
                fileService.sendHtml(ctx, HtmlGenerator.generateDriveList(nickname));
                return;
            }
        }

        //通用文件处理
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

    private void handleLogin(ChannelHandlerContext ctx, FullHttpRequest req) {
        UserSession user = ctx.channel().attr(AuthService.SESSION_KEY).get();
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
                LogPanel.log("[Auth] "+user.getUserId()+": 登录成功 (Device: " + user.getDeviceName() + ")");
                user.setValuable(true);
                // 更新昵称
                if (!inputNickname.isEmpty()) {
                    final String nick = inputNickname;
                    Platform.runLater(() -> user.setNickname(nick));
                }
                user.updateLastActive();

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

        return user.getUserId();
    }
}