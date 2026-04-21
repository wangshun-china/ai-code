package com.ws.codecraft.controller;

import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.common.BaseResponse;
import com.ws.codecraft.common.ResultUtils;
import com.ws.codecraft.config.CodeProjectProperties;
import com.ws.codecraft.constant.UserConstant;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.exception.ThrowUtils;
import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AppSourceFileContentVO;
import com.ws.codecraft.model.vo.AppSourceFileNodeVO;
import com.ws.codecraft.service.AppService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Read-only generated source file browser.
 */
@RestController
@RequestMapping("/api/app/files")
public class AppFileController {

    private static final long MAX_READ_BYTES = 1024 * 1024;

    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".html",
            ".css",
            ".js",
            ".ts",
            ".vue",
            ".json",
            ".md",
            ".txt",
            ".xml",
            ".yml",
            ".yaml",
            ".properties",
            ".java",
            ".jsx",
            ".tsx"
    );

    @Resource
    private AppService appService;

    @Resource
    private CodeProjectProperties codeProjectProperties;

    @GetMapping("/tree")
    public BaseResponse<List<AppSourceFileNodeVO>> listSourceFiles(@RequestParam Long appId,
                                                                   HttpServletRequest request) {
        App app = validateAppAccess(appId, request);
        Path rootPath = getAppSourceRoot(app);
        ThrowUtils.throwIf(!Files.exists(rootPath) || !Files.isDirectory(rootPath),
                ErrorCode.NOT_FOUND_ERROR, "应用代码目录不存在，请先生成代码");
        return ResultUtils.success(listChildren(rootPath, rootPath));
    }

    @GetMapping("/content")
    public BaseResponse<AppSourceFileContentVO> getSourceFileContent(@RequestParam Long appId,
                                                                     @RequestParam String path,
                                                                     HttpServletRequest request) {
        App app = validateAppAccess(appId, request);
        ThrowUtils.throwIf(StrUtil.isBlank(path), ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        Path rootPath = getAppSourceRoot(app);
        Path targetPath = safeResolve(rootPath, path);
        ThrowUtils.throwIf(!Files.exists(targetPath) || !Files.isRegularFile(targetPath),
                ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        ThrowUtils.throwIf(!isTextFile(targetPath), ErrorCode.PARAMS_ERROR, "仅支持查看文本文件");

        try {
            long size = Files.size(targetPath);
            ThrowUtils.throwIf(size > MAX_READ_BYTES, ErrorCode.PARAMS_ERROR, "文件过大，暂不支持预览");

            AppSourceFileContentVO contentVO = new AppSourceFileContentVO();
            contentVO.setName(targetPath.getFileName().toString());
            contentVO.setPath(toRelativePath(rootPath, targetPath));
            contentVO.setContent(Files.readString(targetPath, StandardCharsets.UTF_8));
            contentVO.setLanguage(resolveLanguage(targetPath.getFileName().toString()));
            contentVO.setSize(size);
            return ResultUtils.success(contentVO);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
    }

    private App validateAppAccess(Long appId, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        User loginUser = InnerUserService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该应用源码");
        }
        return app;
    }

    private Path getAppSourceRoot(App app) {
        String sourceDirName = app.getCodeGenType() + "_" + app.getId();
        return Path.of(codeProjectProperties.getOutputRootDir(), sourceDirName).toAbsolutePath().normalize();
    }

    private List<AppSourceFileNodeVO> listChildren(Path rootPath, Path currentPath) {
        try (var stream = Files.list(currentPath)) {
            return stream
                    .filter(path -> !IGNORED_NAMES.contains(path.getFileName().toString()))
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(path -> toNode(rootPath, path))
                    .toList();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件列表失败");
        }
    }

    private AppSourceFileNodeVO toNode(Path rootPath, Path path) {
        AppSourceFileNodeVO node = new AppSourceFileNodeVO();
        node.setName(path.getFileName().toString());
        node.setPath(toRelativePath(rootPath, path));
        boolean directory = Files.isDirectory(path);
        node.setDirectory(directory);
        if (directory) {
            node.setChildren(listChildren(rootPath, path));
        } else {
            try {
                node.setSize(Files.size(path));
            } catch (IOException ignored) {
                node.setSize(0L);
            }
        }
        return node;
    }

    private Path safeResolve(Path rootPath, String relativePath) {
        Path resolvedPath = rootPath.resolve(relativePath).toAbsolutePath().normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法文件路径");
        }
        for (Path segment : rootPath.relativize(resolvedPath)) {
            if (IGNORED_NAMES.contains(segment.toString())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法文件路径");
            }
        }
        return resolvedPath;
    }

    private String toRelativePath(Path rootPath, Path path) {
        return rootPath.relativize(path).toString().replace("\\", "/");
    }

    private boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return TEXT_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private String resolveLanguage(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".vue")) {
            return "vue";
        }
        if (lowerName.endsWith(".ts") || lowerName.endsWith(".tsx")) {
            return "typescript";
        }
        if (lowerName.endsWith(".js") || lowerName.endsWith(".jsx")) {
            return "javascript";
        }
        if (lowerName.endsWith(".css")) {
            return "css";
        }
        if (lowerName.endsWith(".html")) {
            return "html";
        }
        if (lowerName.endsWith(".json")) {
            return "json";
        }
        if (lowerName.endsWith(".md")) {
            return "markdown";
        }
        return "text";
    }
}
