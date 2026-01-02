module com.ccb.javafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;
    requires atlantafx.base;
    requires io.netty.transport;
    requires io.netty.codec.http;
    requires io.netty.handler;
    requires io.netty.buffer;
    requires io.netty.common;
    requires java.desktop;
    requires com.google.zxing;
    requires com.google.zxing.javase;

    opens com.vc6 to javafx.fxml;
    exports com.vc6;
}