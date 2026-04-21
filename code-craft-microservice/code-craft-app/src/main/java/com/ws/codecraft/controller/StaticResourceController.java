package com.ws.codecraft.controller;

import com.ws.codecraft.config.CodeProjectProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

/**
 * 静态资源预览控制器。
 */
@RestController
@RequestMapping("/api/static")
@Slf4j
@RequiredArgsConstructor
public class StaticResourceController {

    private final CodeProjectProperties codeProjectProperties;

    @GetMapping("/**")
    public ResponseEntity<Resource> serveStaticResource(HttpServletRequest request) {
        try {
            String requestURI = request.getRequestURI();
            String prefix = "/api/static/";
            if (!requestURI.contains(prefix)) {
                return ResponseEntity.notFound().build();
            }

            String resourcePath = requestURI.substring(requestURI.indexOf(prefix) + prefix.length());
            if (resourcePath.isEmpty() || resourcePath.endsWith("/")) {
                resourcePath += "index.html";
            }

            String filePath = codeProjectProperties.getOutputRootDir() + File.separator + resourcePath;
            if (File.separator.equals("\\")) {
                filePath = filePath.replace("/", "\\");
            }

            File file = new File(filePath);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve static resource {}", request.getRequestURI(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (filePath.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (filePath.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (filePath.endsWith(".png")) {
            return "image/png";
        }
        if (filePath.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
