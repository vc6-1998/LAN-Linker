package com.vc6.core.service;

import com.vc6.model.AppConfig;
import com.vc6.model.UserSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.Set;

public class AuthService {

    private static final String AUTH_COOKIE = "LAN_LINKER_AUTH"; // 用于 PIN 码登录
    private static final String UID_COOKIE = "LAN_LINKER_UID";   // 用于标识设备身份
    public static final AttributeKey<UserSession> SESSION_KEY = AttributeKey.valueOf("USER_SESSION");
    /**
     * 【入口方法】识别并登记用户
     * @param ctx Netty 上下文
     * @param req 请求对象
     * @param resp 准备返回的响应对象 (用于种下新 Cookie)
     */
    public UserSession identifyUser(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponse resp) {
        String ip = "Unknown";
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress addr) {
            ip = addr.getAddress().getHostAddress();
        }

        String uid = getUidFromCookie(req);
        UserSession session = null;

        // 1. 尝试找回老会话 (通过 UID 或 IP)
        if (uid != null) {
            session = SessionManager.getInstance().getSession(uid);
        }
        if (session == null) {
            session = SessionManager.getInstance().findSessionByIp(ip);
        }

        // 2. 如果没找到，创建新会话
        if (session == null) {
            uid = java.util.UUID.randomUUID().toString().substring(0, 8);
            String ua = req.headers().get(HttpHeaderNames.USER_AGENT);
            session = SessionManager.getInstance().getOrCreateSession(uid, ip, ua);

            // 种下 Cookie
            Cookie c = new DefaultCookie("LAN_LINKER_UID", uid);
            c.setMaxAge(60 * 60 * 24 * 365);
            c.setPath("/");
            c.setSecure(false);
            resp.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.LAX.encode(c));
        }

        // 3. 更新活跃时间
        session.updateLastActive();

        return session;
    }

    /**
     * 【私有辅助方法】从请求头解析 UID
     */
    private String getUidFromCookie(FullHttpRequest req) {
        String cookieHeader = req.headers().get(HttpHeaderNames.COOKIE);
        // 调试日志
        if (cookieHeader != null) {
            Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieHeader);
            for (Cookie c : cookies) {
                if (UID_COOKIE.equals(c.name())) {
                    return c.value();
                }
            }
        }
        return null;
    }

    // ==========================================
    // 下面是原有的 PIN 码登录逻辑 (保持不变)
    // ==========================================

    public boolean isConfiguredAndLoggedIn(FullHttpRequest req) {
        String serverPin = AppConfig.getInstance().getRemotePin();
        if (serverPin == null || serverPin.trim().isEmpty()) return true;

        String cookieHeader = req.headers().get(HttpHeaderNames.COOKIE);
        if (cookieHeader == null) return false;

        Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieHeader);
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE.equals(cookie.name())) {
                return serverPin.equals(cookie.value());
            }
        }
        return false;
    }

    public boolean verifyPin(String inputPin) {
        String serverPin = AppConfig.getInstance().getRemotePin();
        return serverPin != null && serverPin.equals(inputPin);
    }

    public String createAuthCookie() {
        String pin = AppConfig.getInstance().getRemotePin();
        Cookie cookie = new DefaultCookie(AUTH_COOKIE, pin == null ? "" : pin);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        // 【核心修改】根据配置设置有效期
        int days = AppConfig.getInstance().getSessionExpiryDays();
        if (days == 0) {
            // 设置为 -1，浏览器关闭时自动删除 Cookie
            cookie.setMaxAge(-1);
        } else {
            // 转换为秒
            cookie.setMaxAge(60 * 60 * 24 * days);
        }

        return ServerCookieEncoder.LAX.encode(cookie);
    }
}