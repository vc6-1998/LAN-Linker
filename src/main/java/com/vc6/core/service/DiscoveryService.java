package com.vc6.core.service;

import com.vc6.core.NettyServer;
import com.vc6.model.AppConfig;
import com.vc6.model.RemoteDevice;
import com.vc6.model.ServerMode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class DiscoveryService {
    private static final DiscoveryService INSTANCE = new DiscoveryService();
    private static final int UDP_PORT = 59998;
    private static final String CMD_SCAN = "LAN_LINKER_SCAN";
    private static final String CMD_REPLY = "LAN_LINKER_REPLY";

    private final ObservableList<RemoteDevice> foundDevices = FXCollections.observableArrayList();
    private DatagramSocket socket;
    private boolean isRunning = false;

    private DiscoveryService() {
        startListener();
        startCleaner(); // 启动超时清理任务
    }

    public static DiscoveryService getInstance() { return INSTANCE; }

    private void startListener() {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(UDP_PORT);
                socket.setBroadcast(true);
                isRunning = true;
                byte[] buf = new byte[1024];
                while (isRunning && !socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleMessage(msg, packet.getAddress());
                }
            } catch (Exception e) {
                System.err.println("雷达端口占用: " + e.getMessage());
            }
        }).start();
    }

    private void handleMessage(String msg, InetAddress senderIp) {
        try {
            if (senderIp.getHostAddress().equals(InetAddress.getLocalHost().getHostAddress())) return;
        } catch (Exception ignored) {}

        String[] parts = msg.split("\\|");

        if (parts[0].equals(CMD_SCAN)) {
            // 只有当服务开启且允许被发现时才回复
            if (AppConfig.getInstance().isDiscoveryEnabled() &&
                    NettyServer.getInstance().isRunning()) {
                replyIdentity(senderIp);
            }
        }

        if (parts[0].equals(CMD_REPLY) && parts.length >= 3) {
            String name = parts[1];
            int port = Integer.parseInt(parts[2]);
            String ip = senderIp.getHostAddress();

            Platform.runLater(() -> {
                // 【核心修复】检查是否已存在
                boolean found = false;
                for (RemoteDevice d : foundDevices) {
                    if (d.getIp().equals(ip) && d.getPort() == port) {
                        d.updateLastSeen(); // 存在则续命
                        // 如果名字变了，也可以顺便更新
                        if (!d.getName().equals(name)) {
                            // d.setName(name); // 如果 RemoteDevice 支持改名
                        }
                        found = true;
                        break;
                    }
                }
                // 不存在则添加
                if (!found) {
                    foundDevices.add(new RemoteDevice(ip, port, name));
                }
            });
        }
    }

    public void scan() {
        if (socket == null || socket.isClosed()) return;
        // 【核心修复】扫描时不再清空列表！防止闪烁
        new Thread(() -> {
            try {
                byte[] data = CMD_SCAN.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), UDP_PORT);
                socket.send(packet);
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    /**
     * 定时清理离线设备 (每5秒检查一次，移除超过15秒没反应的)
     */
    private void startCleaner() {
        new Timer(true).schedule(new TimerTask() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long timeout = 15000; // 15秒超时

                Platform.runLater(() -> {
                    // 移除超时设备
                    foundDevices.removeIf(d -> (now - d.getLastSeen()) > timeout);
                });
            }
        }, 5000, 5000);
    }

    private void replyIdentity(InetAddress target) {
        try {
            String name = AppConfig.getInstance().getdeviceName();
            int port = AppConfig.getInstance().getPort();
            String reply = CMD_REPLY + "|" + name + "|" + port;
            byte[] data = reply.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, target, UDP_PORT);
            socket.send(packet);
        } catch (Exception e) {}
    }

    public void stop() {
        isRunning = false;
        if (socket != null) socket.close();
    }

    public ObservableList<RemoteDevice> getFoundDevices() { return foundDevices; }
}