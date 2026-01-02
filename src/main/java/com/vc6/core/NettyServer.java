package com.vc6.core;


import com.vc6.core.handler.HttpRequestHandler;
import com.vc6.gui.component.LogPanel;
import com.vc6.model.AppConfig;
import com.vc6.model.ServerMode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import javafx.application.Platform;

public class NettyServer {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture channelFuture;

    private static NettyServer instance;
    private static GlobalTrafficShapingHandler trafficHandler;

    // Getter 保持不变 (注意判空)
    public static GlobalTrafficShapingHandler getTrafficHandler() {
        return trafficHandler;
    }

    public static synchronized NettyServer getInstance() {
        if (instance == null) {
            instance = new NettyServer();
        }
        return instance;
    }

    private NettyServer() {
    }

    public boolean isRunning() {
        return bossGroup != null && !bossGroup.isShutdown();
    }

    /**
     * 启动服务器 (带模式)
     */
    public void start(ServerMode mode) {
        // 如果已经在运行，先停止，再启动
        if (isRunning()) {
            stop();
            // 简单等待资源释放
            try { Thread.sleep(200); } catch (Exception e) {}
        }

        new Thread(() -> {
            // 1. 防止 UI 状态不一致，先重置为停止
            updateStatus(ServerMode.STOPPED);

            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            trafficHandler = new GlobalTrafficShapingHandler(new java.util.concurrent.ScheduledThreadPoolExecutor(1), 1000);
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                if (trafficHandler != null) ch.pipeline().addLast(trafficHandler);
                                ch.pipeline().addLast(new HttpServerCodec());
                                long maxMb = AppConfig.getInstance().getMaxFileSizeMb();
                                int maxBytes = (int) Math.min(maxMb * 1024 * 1024, Integer.MAX_VALUE);
                                ch.pipeline().addLast(new HttpObjectAggregator(maxBytes));
                                ch.pipeline().addLast(new ChunkedWriteHandler());
                                ch.pipeline().addLast(new HttpRequestHandler());
                            }
                        });

                LogPanel.log("[System] 正在启动模式: " + mode.getDescription() + " ...");

                int currentPort = AppConfig.getInstance().getPort();

                LogPanel.log("[System] 正在绑定端口 " + currentPort + " ...");

                // 使用读取到的 currentPort
                channelFuture = b.bind(currentPort).sync();

                LogPanel.log("[Success] 服务启动成功！端口: " + currentPort);
                updateStatus(mode);

                channelFuture.channel().closeFuture().sync();

            } catch (Exception e) {
                LogPanel.log("[Error] 启动失败: " + e.getMessage());
                updateStatus(ServerMode.STOPPED);
            } finally {
                stopResources();
            }
        }).start();
    }

    public void stop() {
        if (channelFuture != null) {
            try {
                channelFuture.channel().close().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        stopResources();
        if (trafficHandler != null) {
            trafficHandler.release();
            trafficHandler = null;
        }
        // 3. 【停止】更新 AppConfig
        updateStatus(ServerMode.STOPPED);
        LogPanel.log("[System] 服务已停止。");
    }

    private void stopResources() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    // 辅助方法：在 JavaFX 线程更新状态
    private void updateStatus(ServerMode mode) {
        Platform.runLater(() ->
                AppConfig.getInstance().setServerMode(mode)
        );
    }
}