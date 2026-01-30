package com.vc6.core.service;

import com.vc6.model.AppConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import org.apache.commons.text.StringEscapeUtils;

public class HtmlGenerator {

    // --- 样式定义 ---
    private static final String CUSTOM_CSS = """
        <style>
            :root {
                --bs-body-bg: #0d1117; --bs-body-color: #c9d1d9;
                --card-bg: #161b22; --border-color: #30363d; --accent: #58a6ff;
            }
            body { background-color: var(--bs-body-bg); font-family: -apple-system, system-ui, "Microsoft YaHei", sans-serif; }
            .navbar { background-color: var(--card-bg) !important; border-bottom: 1px solid var(--border-color); }
            .card { background-color: var(--card-bg); border: 1px solid var(--border-color); border-radius: 8px; }
            .list-group-item { background-color: var(--card-bg); border-color: var(--border-color); color: #c9d1d9; transition: 0.2s; }
            .list-group-item:hover { background-color: #21262d; }
            .path-bar { background: #1c2128; border: 1px solid var(--border-color); border-radius: 8px; padding: 10px 18px; display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
            .path-text { font-family: 'Consolas', monospace; color: var(--accent); font-weight: bold; flex-grow: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
            .file-name {
                font-weight: 500; font-size: 15px; color: #fff; text-decoration: none; cursor: pointer;
                overflow-wrap: anywhere;
            }
            .file-name:hover { color: var(--accent); text-decoration: underline; }
            .file-meta { color: #8b949e; font-size: 12px; margin-top: 2px; }
            .btn-action { color: #8b949e; padding: 5px; transition: 0.2s; text-decoration: none; cursor: pointer; }
            .btn-action:hover { color: var(--accent); }
            .btn-del:hover { color: #f85149 !important; }
            .msg-card { border-left: 4px solid var(--accent) !important; }
            .msg-text {
                font-family: 'Consolas', monospace;
                background: #0d1117;
                padding: 12px;
                border-radius: 6px;
                border: 1px solid #222;
                font-size: 14px;
                color: #e6edf3;
                white-space: pre-wrap;
                word-break: break-word;
                overflow-wrap: anywhere;
            }
            #progress-fixed { position: fixed; bottom: 25px; right: 25px; width: 300px; z-index: 2000; display: none; }
            .modal-content { background-color: #0d1117; border: 1px solid var(--border-color); }
            #previewBody img { max-width: 100%; border-radius: 4px; box-shadow: 0 4px 15px rgba(0,0,0,0.5); }
            #previewBody pre { text-align: left; background: #000; color: #85e89d; padding: 15px; border-radius: 6px; font-family: Consolas, monospace; overflow-x: auto; margin: 0; }
            #drop-overlay {
                position: fixed;\s
                top: 0; left: 0;\s
                width: 100vw; height: 100vh;
                background: rgba(13, 17, 23, 0.85);
                border: 4px dashed var(--accent);
                z-index: 9999;
                display: none;
                pointer-events: none;
                align-items: center;\s
                justify-content: center;
            }
            .overlay-content {\s
                text-align: center;
                color: var(--accent);
            }
            .text-clamp {
                 display: -webkit-box;
                 -webkit-line-clamp: 3;
                 -webkit-box-orient: vertical;
                 overflow: hidden;
                 font-family: 'Consolas', monospace;
                 font-size: 14px;
                 color: #e6edf3;
                 word-break: break-all;
                 overflow-wrap: anywhere;
             }
            #web-toast {
                position: fixed; bottom: 30px;
                left: 50%;
                transform: translateX(-50%);
                background: rgba(0,0,0,0.8); color: white; padding: 10px 25px;
                border-radius: 25px; font-size: 14px;
                opacity: 0; transition: opacity 0.3s;
                pointer-events: none; z-index: 10000;
            }
            #web-toast.show { opacity: 1; }
            </style>
    """;

    // --- 脚本逻辑 ---
    private static final String CUSTOM_JS = """
        <script>
            function navigate(url) {
                 fetch(url).then(r => {
                     const finalPath = new URL(r.url).pathname;
                     window.history.pushState({path: finalPath}, '', finalPath);
                     return r.text();
                 }).then(html => {
                     const parser = new DOMParser();
                     const doc = parser.parseFromString(html, 'text/html');
                     const content = doc.getElementById('main-content');
                     if (content) {
                          document.getElementById('main-content').innerHTML = content.innerHTML;
                     } else {
                          location.reload();
                     }
                 }).catch(e => {
                     console.error("Nav Error", e);
                     location.reload();
                 });
             }
            window.onpopstate = () => location.reload();
            function showToast(msg) {
                const t = document.getElementById('web-toast');
                t.innerText = msg;
                t.classList.add('show');
                setTimeout(() => t.classList.remove('show'), 2000);
            }
            function copy(text) {
                if (navigator.clipboard && window.isSecureContext) {
                    navigator.clipboard.writeText(text).then(() => showToast('已复制到剪贴板'));
                } else {
                    const ta = document.createElement("textarea");
                    ta.value = text;
                    ta.style.position = "fixed";
                    document.body.appendChild(ta);
                    ta.focus(); ta.select();
                    try {
                        document.execCommand('copy');
                        showToast('已复制到剪贴板');
                    } catch (err) {
                        alert('复制失败，请手动复制');
                    }
                    document.body.removeChild(ta);
                }
            }
            function preview(url, type, name, size) {
                 const modal = new bootstrap.Modal(document.getElementById('previewModal'));
                 const header = document.querySelector('#previewModal .modal-header');
                 const footer = document.querySelector('#previewModal .modal-footer');
                 const body = document.getElementById('previewBody');
    
                 const oldHeaderBtn = document.getElementById('header-copy-btn');
                 if(oldHeaderBtn) oldHeaderBtn.remove();

                 const dlBtn = document.getElementById('dl-btn');
                 dlBtn.href = url;
                 dlBtn.setAttribute('download', name);

                 if (type === 'img') {
                     body.innerHTML = `<img src="${url}" class="img-fluid rounded">`;
                 } else if (type === 'txt') {
                     if (size > 100 * 1024)
                     {
                        body.innerHTML = `
                         <div class="py-5 text-center">
                             <i class="bi bi-file-earmark-x fs-1 text-secondary"></i>
                             <p class="mt-3 text-secondary">文本文件过大，不支持预览</p>
                             <p class="small text-muted">请点击右侧下载图标或下方按钮下载后查看</p>
                         </div>`;
                     }
                     else
                     {
                         fetch(url).then(r => r.text()).then(t => {
                          // 渲染内容
                          body.innerHTML = `<pre><code>${t.replace(/</g, '&lt;')}</code></pre>`;
        
                          // 创建按钮 (此时 t 是可用的)
                          const copyBtn = document.createElement('button');
                          copyBtn.id = 'header-copy-btn';
                          copyBtn.className = 'btn btn-sm btn-outline-primary ms-auto';
                          copyBtn.innerHTML = '<i class="bi bi-clipboard"></i>复制';
                          copyBtn.onclick = () => copy(t); // 绑定 t
                          header.insertBefore(copyBtn, header.lastElementChild);
                      });
                     }
                 } else {
                     body.innerHTML = `
                         <div class="py-5 text-center">
                             <i class="bi bi-file-earmark-x fs-1 text-secondary"></i>
                             <p class="mt-3 text-secondary">该文件类型不支持在线预览</p>
                             <p class="small text-muted">请点击右侧下载图标或下方按钮下载后查看</p>
                         </div>`;
                 }
                 modal.show();
             }
            async function doUpload(fileList) {
                  if (!fileList.length) return;
                  const maxBytes = window.MAX_FILE_SIZE_MB * 1024 * 1024;
                  const panel = document.getElementById('progress-fixed');
                  const bar = document.getElementById('task-bar');
                  const percent = document.getElementById('task-percent');
                  const nameLabel = document.getElementById('task-name');
  
                  panel.style.display = 'block';
  
                  // 逐个上传
                  for (let i = 0; i < fileList.length; i++) {
                      const file = fileList[i];
                      nameLabel.innerText = `(${i + 1}/${fileList.length}) ${file.name}`;
                      console.log(file.size);
                      if (file.size > maxBytes) {
                          alert(`文件大小超出限制！\\n"${file.name}"\\n当前文件大小: ${(file.size/1024/1024).toFixed(2)} MB\\n限制: ${window.MAX_FILE_SIZE_MB} MB`);
                          continue; 
                      }
                      // 等待单个文件上传完成
                      await new Promise((resolve, reject) => {
                          const fd = new FormData();
                          fd.append("file", file);
                          const xhr = new XMLHttpRequest();
                         \s
                          let uploadUrl = window.location.pathname;
                          if (!uploadUrl.endsWith('/')) uploadUrl += '/';
  
                          xhr.open("POST", uploadUrl, true);
                          xhr.upload.onprogress = (e) => {
                              if (e.lengthComputable) {
                                  let p = Math.round((e.loaded / e.total) * 100);
                                  // 【关键修复】确保这里是双百分号，用于 Java 转义
                                  bar.style.width = p + '%%';
                                  percent.innerText = p + '%%';
                              }
                          };
                          xhr.onload = () => resolve();
                          xhr.onerror = () => {
                              console.warn("XHR Error:", file.name);
                              alert(file.name + ' 上传失败！不允许上传文件夹！');
                              resolve(); 
                          };
                          try {
                              xhr.send(fd);
                          } catch (e) {
                              console.warn("Upload blocked:", e);
                              panel.style.display = 'none';
                              alert(file.name + ' 上传失败！');
                              resolve(); 
                          }
                      });
                  }
  
                  // 全部完成后刷新

                  setTimeout(() => {
                      navigate(window.location.href);
                      panel.style.display = 'none';
                  }, 1000);
              }
              
            function checkSubmit(e) {
                if (e.ctrlKey && e.key === 'Enter') {
                    e.target.form.submit();
                }
            }
            function checkLen(el) {
                const len = el.value.length;
                const max = window.MAX_TEXT_LEN;
                const count = document.getElementById('char-count');
                const btn = document.getElementById('msg-btn');
                count.innerText = len + " / " + max;
                if (len > max) {
                    count.className = 'text-danger fw-bold small'; // 超出变红
                    btn.disabled = true; // 禁用按钮
                } else {
                    count.className = 'text-secondary small'; // 正常灰色
                    btn.disabled = false; // 启用按钮
                }
            }
              
            function showMkdir() {
                const m = new bootstrap.Modal(document.getElementById('mkdirModal'));
                const input = document.getElementById('mkdirInput');
                input.value = ''; // 清空
                m.show();
                // 自动聚焦 (延迟一下等待模态框动画)
                setTimeout(() => input.focus(), 500);
            }
            function submitMkdir() {
                const name = document.getElementById('mkdirInput').value;
                
                if (name) {
                    const invalid = /[<>:"/\\\\|?*]/;
                    if (invalid.test(name)) {
                        alert("文件名不能包含以下字符：< > : \\" / \\\\ | ? *");
                        return;
                    }
                    const modal = bootstrap.Modal.getInstance(document.getElementById('mkdirModal'));
                    modal.hide();
                    // 发送请求
                    navigate(window.location.pathname + "?action=mkdir&name=" + encodeURIComponent(name));
                }
            }
            document.getElementById('mkdirInput').addEventListener('keypress', function (e) {
                if (e.key === 'Enter') submitMkdir();
            });
            window.onbeforeunload = function (e) {
                const panel = document.getElementById('progress-fixed');
                // 如果进度条面板正在显示（即正在上传），弹出确认框
                if (panel && panel.style.display === 'block') {
                    e.preventDefault();
                    e.returnValue = ''; // 现代浏览器需要设置这个值来触发提示
                }
            };
            let dragCounter = 0; // 使用计数器解决进入/离开子元素时的闪烁问题
            window.addEventListener('dragenter', (e) => {
                e.preventDefault();
                dragCounter++;
                document.getElementById('drop-overlay').style.display = 'flex';
            });

            window.addEventListener('dragleave', (e) => {
                e.preventDefault();
                dragCounter--;
                if (dragCounter === 0) {
                    document.getElementById('drop-overlay').style.display = 'none';
                }
            });

            window.addEventListener('dragover', (e) => {
                e.preventDefault(); // 必须阻止默认行为，drop事件才会触发
            });

            window.addEventListener('drop', (e) => {
                e.preventDefault();
                dragCounter = 0;
                document.getElementById('drop-overlay').style.display = 'none';
               \s
                // 获取拖入的文件流，直接调用你现有的上传函数
                const files = e.dataTransfer.files;
                if (files.length > 0) {
                    doUpload(files);
                }
            });
            
            document.addEventListener('paste', (e) => {
                // 如果焦点在输入框，且粘贴的是纯文本，让浏览器默认处理
                const isInput = document.activeElement.tagName === 'TEXTAREA' || document.activeElement.tagName === 'INPUT';
               \s
                // 1. 检查是否有文件 (包括截图)
                if (e.clipboardData.items) {
                    const items = e.clipboardData.items;
                    const filesToUpload = [];

                    for (let i = 0; i < items.length; i++) {
                        if (items[i].kind === 'file') {
                            const file = items[i].getAsFile();
                            // 如果是截图(image/png)且名字是默认的'image.png'，给它个时间戳名字
                            if (file.type.startsWith('image/') && file.name === 'image.png') {
                                const newName = "picture_" + Date.now() + ".png";
                                // 创建新文件对象以重命名
                                filesToUpload.push(new File([file], newName, { type: file.type }));
                            } else {
                                filesToUpload.push(file);
                            }
                        }
                    }
                   \s
                    // 如果找到了文件/截图，直接上传
                    if (filesToUpload.length > 0) {
                        e.preventDefault();
                        doUpload(filesToUpload);
                        return;
                    }
                }
               \s
                // 2. 检查是否有纯文本 (且焦点不在输入框时)
                if (!isInput) {
                    const text = e.clipboardData.getData('text');
                    if (text) {
                        e.preventDefault();
                        const input = document.querySelector('textarea[name="content"]');
                        if (input) {
                            input.value = text;
                            // 可选：直接提交，或者聚焦让用户点发送
                            // document.querySelector('form button').click();\s
                            input.focus();
                        }
                    }
                }
            });
        </script>
    """;

    // --- 内部 Shell 辅助 ---
    private static String getHead(String subtitle, String nickname) {
        String badgeText = (nickname != null && !nickname.isEmpty()) ? nickname : subtitle;
        String appTitle = AppConfig.getInstance().getdeviceName();
        return String.format("""
            <!DOCTYPE html>
            <html lang="zh-CN" data-bs-theme="dark">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <link href="/static/bootstrap.min.css" rel="stylesheet">
                <link href="/static/bootstrap-icons.css" rel="stylesheet">
                %s
            </head>
            <body>
                <div id="drop-overlay">
                    <div class="overlay-content">
                        <i class="bi bi-cloud-arrow-up-fill text-primary" style="font-size: 4rem;"></i>
                        <h2 class="mt-3">释放鼠标开始上传</h2>
                        <p class="text-secondary">文件将直接上传到当前目录</p>
                    </div>
                </div>
                <nav class="navbar navbar-dark sticky-top shadow-sm">
                    <div class="container-fluid px-4">
                        <a class="navbar-brand fw-bold" href="/"><i class="bi bi-hdd-network text-primary me-2"></i>%s</a>
                        <span class="badge bg-primary-subtle text-primary border border-primary-subtle px-3">%s</span>
                    </div>
                </nav>
                <div class="container my-4" style="max-width: 900px;" id="main-content">
            """,appTitle, CUSTOM_CSS, appTitle, badgeText);
    }

    private static String getFoot() {

        long maxMb = AppConfig.getInstance().getMaxFileSizeMb();
        long maxLen = AppConfig.getInstance().getMaxTextLength();
        return String.format("""
            </div>
            
            <div id="progress-fixed" class="card shadow-lg border-primary">
                  <div class="card-body py-2">
                      <div class="d-flex justify-content-between mb-1 small fw-bold">
                          <span id="task-name" class="text-truncate" style="max-width:180px;">准备中...</span>
                          <span id="task-percent">0%%</span>
                      </div>
                      <div class="progress" style="height:8px;">
                          <!-- 【检查】这里必须有 bg-success 或者 bg-primary，否则没颜色 -->
                          <div id="task-bar" class="progress-bar progress-bar-striped progress-bar-animated bg-success" style="width: 0%%"></div>
                      </div>
                  </div>
              </div>
              <div id="web-toast"></div>
             <div class="modal fade" id="mkdirModal" tabindex="-1">
               <div class="modal-dialog modal-dialog-centered">
                 <div class="modal-content">
                   <div class="modal-header">
                     <h6 class="modal-title"><i class="bi bi-folder-plus me-2"></i>新建文件夹</h6>
                     <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                   </div>
                   <div class="modal-body">
                     <input type="text" id="mkdirInput" class="form-control bg-dark text-white border-secondary" placeholder="输入文件夹名称" autocomplete="off">
                   </div>
                   <div class="modal-footer p-2">
                     <button type="button" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">取消</button>
                     <button type="button" class="btn btn-sm btn-primary" onclick="submitMkdir()">创建</button>
                   </div>
                 </div>
               </div>
             </div>
            <div class="modal fade" id="previewModal" tabindex="-1"><div class="modal-dialog modal-xl modal-dialog-centered"><div class="modal-content">
                <div class="modal-header"><h6>预览</h6><button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
                <div class="modal-body" id="previewBody"></div>
                <div class="modal-footer p-2"><a id="dl-btn" class="btn btn-sm btn-primary px-3" download>下载原文件</a></div>
            </div></div></div>
            <script>
                window.MAX_FILE_SIZE_MB = %d;
                window.MAX_TEXT_LEN = %d;
            </script>
            <footer class="text-center py-4 mt-5 text-secondary" style="font-size: 12px; opacity: 0.6;">
                <hr class="border-secondary mb-3 opacity-25">
                <div>Powered by <span class="text-light">Netty</span> & <span class="text-light">JavaFX</span></div>
                <div class="mt-1">Designed for Local Network High-Speed Transfer</div>
            </footer>
            <script src="/static/bootstrap.bundle.min.js"></script>
            """ + CUSTOM_JS + "</body></html>", maxMb, maxLen);
    }

    // ================= 公开页面方法 =================

    public static String generateFileList(File dir, String uri,String nickname) {
        StringBuilder buf = new StringBuilder();
        buf.append(getHead(uri,nickname));
        buf.append("<div class='path-bar'><i class='bi bi-folder2-open text-warning fs-5'></i><div class='path-text'>").append(uri).append("</div>");
        if (AppConfig.getInstance().isAllowUpload()) {
            buf.append("<button class='btn btn-sm btn-outline-secondary px-3 fw-bold me-2' onclick='showMkdir()'><i class='bi bi-folder-plus me-1'></i>新建</button>");
            buf.append("<button class='btn btn-sm btn-primary px-3 fw-bold' onclick='document.getElementById(\"fi\").click()'><i class='bi bi-upload'></i>上传</button>");
            buf.append("<input type='file' id='fi' onchange='doUpload(this.files)' hidden multiple>");
        }
        buf.append("<a href='javascript:navigate(location.pathname)' class='btn-action'><i class='bi bi-arrow-clockwise'></i></a></div>");
        buf.append("<div class='list-group shadow-sm border border-secondary rounded overflow-hidden'>");
        if (!"/".equals(uri) && !uri.isEmpty()) {
            buf.append(String.format("<a href='javascript:void(0)' onclick='navigate(\"%s\")' class='list-group-item list-group-item-action py-3'><i class='bi bi-arrow-90deg-up me-3 text-warning'></i>.. 返回上一级</a>", resolveParent(uri)));
        }
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> f1.isDirectory() != f2.isDirectory() ? (f1.isDirectory() ? -1 : 1) : f1.getName().compareToIgnoreCase(f2.getName()));
            for (File f : files) { if (f.isHidden() || !f.canRead()) continue; appendFileRow(buf, f, uri); }
        }
        buf.append("</div>").append(getFoot());
        return buf.toString();
    }

    public static String generateQuickSharePage(File dir, String nickname) {
        StringBuilder buf = new StringBuilder();
        buf.append(getHead("极速快传", nickname));

        // 顶部输入框 (完全保留你的代码)
        buf.append("""
            <div class="card p-3 mb-4 shadow-sm">
                <form action="/api/text" method="post" class="input-group mb-3">
        
                <textarea name="content" class="form-control bg-dark text-white border-secondary"\s
                          placeholder="发送文本消息 (支持 Ctrl+Enter)..." rows="2" required\s
                          onkeydown="checkSubmit(event)" oninput="checkLen(this)"></textarea>
        
                <div class="d-flex justify-content-between align-items-center">
                    <span id="char-count" class="text-secondary small">0 / --</span>
                     <button id="msg-btn" class="btn btn-primary" type="submit" title="Ctrl+Enter 发送">
                    <i class="bi bi-send-fill"></i></button>
                </div>
        
                </form>
                <div class="text-center py-2 border-secondary border-dashed rounded" style="border: 2px dashed #333; cursor:pointer;" onclick="document.getElementById('fi').click()">
                    <i class="bi bi-cloud-arrow-up fs-3 text-secondary"></i><br><small class="text-secondary">点击或拖拽文件上传</small>
                </div>
                <input type="file" id="fi" onchange="doUpload(this.files)" hidden multiple>
            </div>
            <div class="d-flex justify-content-between align-items-center mb-3 px-1">
                <h6 class="text-secondary m-0"><i class="bi bi-clock-history me-2"></i>传输时间轴</h6>
                <button onclick="location.reload()" class="btn btn-sm btn-outline-secondary border-0"><i class="bi bi-arrow-clockwise fs-5"></i></button>
            </div>
        """);

        // 列表容器
        buf.append("<div class='list-group list-group-flush border rounded border-secondary overflow-hidden shadow-sm'>");

        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            for (File f : files) {
                appendFileRow(buf, f, "");
            }
        }
        buf.append(getFoot());
        return buf.toString();
    }

    public static String generateLoginPage(String error, String defaultNickname) {
        StringBuilder buf = new StringBuilder();
        // 这里假设 getHead 内部已经处理了 subtitle，我们传入 "安全验证"
        buf.append(getHead("安全验证", null));

        String alertHtml = (error != null) ?
                String.format("<div class='alert alert-danger py-2 small mb-3'><i class='bi bi-exclamation-triangle-fill me-2'></i>%s</div>", error) : "";

        buf.append(String.format("""
            <div class="d-flex justify-content-center align-items-center" style="min-height: 70vh;">
                <div class="card p-4 shadow-lg" style="width: 360px; border-radius: 12px; border-top: 4px solid var(--accent);">
                    <div class="text-center mb-4">
                        <i class="bi bi-shield-lock-fill text-primary" style="font-size: 3rem;"></i>
                        <h4 class="mt-2 text-white fw-bold">访问受限</h4>
                        <p class="text-secondary small">请验证身份以继续访问</p>
                    </div>
                    
                    %s <!-- 错误提示区 -->
                    
                    <form method="post" action="/login">
                        <!-- 1. 用户/设备昵称 (先表明身份) -->
                        <div class="mb-3 text-start">
                            <label class="form-label small text-secondary fw-bold">设备备注 (您的昵称)</label>
                            <div class="input-group">
                                <span class="input-group-text bg-dark border-secondary text-secondary">
                                    <i class="bi bi-person-badge"></i>
                                </span>
                                <input type="text" name="nickname" class="form-control bg-dark text-white border-secondary" 
                                       value="%s" placeholder="给自己起个外号吧" autocomplete="off">
                            </div>
                        </div>
                        
                        <!-- 2. PIN 码 (后输入凭证) -->
                        <div class="mb-3 text-start">
                            <label class="form-label small text-secondary fw-bold">访问 PIN 码</label>
                            <div class="input-group">
                                <span class="input-group-text bg-dark border-secondary text-secondary">
                                    <i class="bi bi-key-fill"></i>
                                </span>
                                <input type="password" name="pin" class="form-control bg-dark text-white border-secondary text-center fs-5"\s
                                       placeholder="4-20 位字母或数字"
                                       required autofocus autocomplete="off"
                                       /* 【核心修复 1】正则表达式改为允许字母和数字 */
                                       pattern="[a-zA-Z0-9]*"
                                       /* 【核心修复 2】改为 text 模式，以便在手机端弹出全键盘而非数字键盘 */
                                       inputmode="text"
                                       style="letter-spacing: 0.3rem;">
                            </div>
                        </div>
                        
                        <button type="submit" class="btn btn-primary w-100 py-2 fw-bold shadow-sm">
                            <i class="bi bi-unlock-fill me-2"></i>解 锁 访 问
                        </button>
                    </form>
                    
                    <div class="mt-4 text-center">
                        <small class="text-muted" style="font-size: 11px; letter-spacing: 1px;">SECURE ACCESS CONTROL</small>
                    </div>
                </div>
            </div>
        """, alertHtml, defaultNickname));

        buf.append(getFoot());
        return buf.toString();
    }

    public static String generateDriveList(String nickname) {
        StringBuilder buf = new StringBuilder();
        buf.append(getHead("我的电脑",nickname));
        buf.append("<div class='list-group shadow-sm border border-secondary rounded'>");
        for (File r : File.listRoots()) {
            String href = "/" + r.getPath().replace("\\", "/");
            buf.append(String.format("""
                <a href="javascript:void(0)" onclick='navigate(\"%s\")' class="list-group-item list-group-item-action d-flex align-items-center py-3">
                    <i class="bi bi-pc-display me-3 text-info fs-4"></i>
                    <div class="flex-grow-1"><div class="fw-bold text-white">%s</div><small class="text-muted">%s 可用</small></div>
                    <i class="bi bi-chevron-right text-muted"></i>
                </a>
            """, href, r.getPath(), formatSize(r.getFreeSpace())));
        }
        buf.append("</div>").append(getFoot());
        return buf.toString();
    }

    // --- 工具类方法 ---

    private static void appendFileRow(StringBuilder buf, File f, String baseUri) {

        String name = f.getName();
        String rawHref = (baseUri.endsWith("/") ? baseUri : baseUri + "/") + name;
        if (!rawHref.startsWith("/")) rawHref = "/" + rawHref;
        String href = encodeUrl(rawHref);
        String safeName = StringEscapeUtils.escapeHtml4(StringEscapeUtils.escapeEcmaScript(name));
        String time = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss").format(new Date(f.lastModified()));

        if (name.endsWith(".lanmsg")) {
            // --- 文本模式 ---
            String fullText = "";
            try { fullText = Files.readString(f.toPath()); } catch(Exception ignored){}

            String displayText = fullText.trim();
            if (displayText.length() > 200) displayText = displayText.substring(0, 200) + "...";
            String[] lines = displayText.split("\n");
            if (lines.length > 5) displayText = String.join("\n", Arrays.copyOf(lines, 5)) + "...";

            String safeFullText = StringEscapeUtils.escapeHtml4(StringEscapeUtils.escapeEcmaScript(fullText));
            String safeDisplay = StringEscapeUtils.escapeHtml4(displayText);

            String icon = "bi-chat-left-text-fill text-warning";
            String contentHtml = String.format("<div class='text-clamp'>%s</div>", safeDisplay);

            String length = String.valueOf(fullText.length());
            String metaHtml = String.format("<div class=\"file-meta text-truncate\">%s &nbsp; %s 字</div>",time,length);

            String mainAction = String.format("onclick=\"preview('%s', 'txt', '%s','%d')\"", href, safeName,f.length());

            // 渲染行
            buf.append(String.format("""
                        <div class="list-group-item d-flex align-items-start py-3">
                            <i class="bi %s fs-4 me-3 flex-shrink-0"></i>
                            <div class="flex-grow-1 min-width-0" style="cursor:pointer" %s>
                                %s
                                %s
                            </div>
                            <div class="ms-3 d-flex gap-2 flex-shrink-0">
                                <button onclick="copy('%s')" class='btn-action border-0 bg-transparent'><i class='bi bi-clipboard'></i></button>
                                <a href="javascript:void(0)" onclick="if(confirm('确定删除？')) navigate('%s?action=delete')" class="btn-action btn-del"><i class="bi bi-trash3"></i></a>
                            </div>
                        </div>
                    """, icon, mainAction, contentHtml, metaHtml, safeFullText, href));
            }
        else {
            String safeNameHTML = StringEscapeUtils.escapeHtml4(name);
            String ext = getExt(name);

            String icon = f.isDirectory() ? "bi-folder-fill text-warning" : "bi-file-earmark-text text-secondary";

            String size = f.isDirectory() ? "" : formatSize(f.length());

            String action = f.isDirectory() ?
                    String.format("href='javascript:void(0)' onclick='navigate(\"%s\")'", href) :
                    String.format("href='javascript:void(0)' onclick=\"preview('%s', '%s', '%s','%d')\"", href, ext, safeName,f.length());

            String downloadBtn = f.isDirectory() ? "" : "<a href='"+href+"' download class='btn-action'><i class='bi bi-download'></i></a>";

            String delBtn = AppConfig.getInstance().isAllowUpload() ?
                    String.format("<a href=\"javascript:void(0)\" onclick=\"if(confirm('确定删除 %s 吗？（该操作不可逆！）')) navigate('%s?action=delete')\" class=\"btn-action btn-del\"><i class=\"bi bi-trash3\"></i></a>",
                            safeName, href) : "";


            buf.append(String.format("""
            <div class="list-group-item d-flex align-items-center py-2">
                <i class="bi %s fs-4 me-3 flex-shrink-0"></i>
        
                <div class="flex-grow-1" style="min-width: 0;">
                    <a %s class="file-name d-block text-truncate">%s</a>
                    <div class="file-meta text-truncate">%s &nbsp; %s</div>
                </div>
                <div class="ms-3 d-flex gap-2 flex-shrink-0">
                    %s
                    %s
                </div>
            </div>
        """, icon, action, safeNameHTML, time, size, downloadBtn ,delBtn));
        }

    }

    private static String resolveParent(String uri) {
        String t = uri.endsWith("/") ? uri.substring(0, uri.length()-1) : uri;
        int i = t.lastIndexOf('/');
        if (i <= 0) return "/";
        String p = t.substring(0, i);
        if (p.matches("^/[a-zA-Z]:$")) p += "/";
        return p;
    }

    private static String formatSize(long l) {
        if (l < 1024) return l + " B";
        int e = (int) (Math.log(l) / Math.log(1024));
        return String.format("%.1f %sB", l / Math.pow(1024, e), "KMGTPE".charAt(e - 1) + "");
    }

    private static String getExt(String n) {
        n = n.toLowerCase();
        if (n.matches(".*\\.(jpg|png|gif|jpeg|webp)$")) return "img";
        if (n.matches(".*\\.(txt|log|java|py|htm|html|css|js|json|xml|yaml|yml|md|c|cpp|properties|sh|h|)$")) return "txt";
        // pdf 不再单独识别，直接走默认的 "bin" 逻辑触发“不支持预览”提示
        return "bin";

    }
    private static String encodeUrl(String path) {
        try {
            return java.net.URLEncoder.encode(path, StandardCharsets.UTF_8)
                    .replace("+","%20")
                    .replace("%2F", "/")
                    .replace("%3A", ":"); // 保留冒号，防止 D%3A 路径错误
        } catch (Exception e) {
            return path;
        }
    }
}