package com.ws.codecraft.controller;

import com.ws.codecraft.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

/**
 * 静态资源预览控制器 (专门用于开发阶段预览)
 * 映射路径：code_output 目录
 */
@RestController
@RequestMapping("/api/static")
public class StaticResourceController {

    // 指向生成的临时目录 (G:\tmp\code_output)
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 访问格式：http://localhost:8126/api/static/html_xxxx/index.html
     */
    @GetMapping("/**")
    public ResponseEntity<Resource> serveStaticResource(HttpServletRequest request) {
        try {
            String requestURI = request.getRequestURI();
            // 调试日志：看看请求到底长啥样
            System.out.println("🔍 [静态预览] 收到请求: " + requestURI);

            // 1. 截取相对路径
            // 把 /api/static/ 替换为空，剩下的就是 html_xxxx/index.html
            String prefix = "/api/static/";
            if (!requestURI.contains(prefix)) {
                return ResponseEntity.notFound().build();
            }
            String resourcePath = requestURI.substring(requestURI.indexOf(prefix) + prefix.length());

            // 2. 默认首页处理
            if (resourcePath.isEmpty() || resourcePath.endsWith("/")) {
                resourcePath += "index.html";
            }

            // 3. 拼接完整物理路径
            String filePath = PREVIEW_ROOT_DIR + File.separator + resourcePath;

            // 4. Windows 路径兼容处理 (防止反斜杠问题)
            if (File.separator.equals("\\")) {
                filePath = filePath.replace("/", "\\");
            }

            System.out.println("🔍 [静态预览] 读取文件: " + filePath);

            // 5. 检查文件
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("❌ 文件不存在");
                return ResponseEntity.notFound().build();
            }

            // 6. 返回文件
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}