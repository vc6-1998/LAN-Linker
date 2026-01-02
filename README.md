# LAN Linker (局域网快传)

![License](https://img.shields.io/badge/license-MIT-blue.svg) ![Java](https://img.shields.io/badge/Java-21-orange) ![JavaFX](https://img.shields.io/badge/JavaFX-Modern-green) ![Netty](https://img.shields.io/badge/Netty-High%20Performance-purple)

**LAN Linker** 是一款基于 JavaFX 和 Netty 开发的高性能局域网文件传输工具。它专为校园网、办公室等局域网环境设计，无需互联网，无需登录账号，即可实现跨设备的文件秒传、剪贴板同步和远程文件管理。

> ? **界面风格**：采用 AtlantaFX (Primer Dark) 打造的现代化深色 UI，告别传统 Java Swing 的陈旧感。

---

## ? 核心功能

### 1. ? 极速快传 (Quick Share)
像微信文件传输助手一样简单，但基于局域网直连，速度飞快。
*   **跨端同步**：电脑端发送文本/文件，手机网页端实时接收（Timeline 时间轴视图）。
*   **无感交互**：支持 `Ctrl+V` 粘贴截图/文本、文件拖拽上传。
*   **自动清理**：支持临时文件自动过期清理，不占硬盘。

### 2. ? 本地目录共享 (Local Share)
将电脑变为高性能文件服务器。
*   **一键共享**：选择任意文件夹，手机扫码即可浏览和下载。
*   **权限控制**：可开关“允许上传/删除”权限，保护文件安全。
*   **断点续传**：基于 Netty 的高效 IO，支持大文件稳定传输。

### 3. ?? 远程全盘访问 (Remote Disk)
*   **私有云体验**：通过 PIN 码验证后，可在手机上浏览电脑所有磁盘（C/D/E盘）。
*   **安全网关**：内置全局安全拦截器，防止未授权访问。

### 4. ? 可视化控制台
*   **实时监控**：动态波形图显示上传/下载速率。
*   **连接助手**：自动生成局域网 IP 二维码，手机扫码即连。
*   **设备管理**：实时查看在线设备列表。

---

## ? 界面预览

| 仪表盘 (Dashboard) | 快传时间轴 (Quick Share) |
| :---: | :---: |
| *(在此处放入你的仪表盘截图)* | *(在此处放入你的快传界面截图)* |

| 手机网页端 (Web UI) | 设置中心 (Settings) |
| :---: | :---: |
| *(在此处放入手机网页截图)* | *(在此处放入设置界面截图)* |

---

## ?? 技术栈

本项目完全遵循“不造轮子但理解轮子”的原则，使用了以下核心技术：

*   **GUI 框架**: JavaFX 21 + AtlantaFX (主题美化) + ControlsFX (高级组件)
*   **网络核心**: Netty 4.1 (高性能 NIO 服务器，手写 HTTP 协议解析)
*   **Web 前端**: Bootstrap 5.3 (响应式布局) + Vanilla JS (AJAX 无刷新上传)
*   **工具库**: ZXing (二维码生成)

---

## ? 快速开始

### 环境要求
*   JDK 21 或更高版本
*   Windows / macOS / Linux

### 运行方式
1.  下载最新版本的 `LAN-Linker.jar`。
2.  确保已安装 Java 21。
3.  双击运行，或在命令行执行：
    ```bash
    java -jar LAN-Linker-1.0-SNAPSHOT.jar
    ```

### 开发构建
```bash
git clone https://github.com/yourname/lan-linker.git
cd lan-linker
mvn clean package
# 构建产物位于 target/ 目录下
```

---

## ? 开发者信息

*   **作者**: [你的名字]
*   **学号**: 11202xxxxxx
*   **课程**: Java 语言程序设计结课作业

---

## ? 许可证

本项目采用 MIT 许可证，详情请参阅 [LICENSE](LICENSE) 文件。