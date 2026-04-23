package com.ws.codecraft.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ws.codecraft.config.CodeProjectProperties;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.exception.ThrowUtils;
import com.ws.codecraft.mapper.AppAttachmentMapper;
import com.ws.codecraft.mapper.AppMapper;
import com.ws.codecraft.model.entity.App;
import com.ws.codecraft.model.entity.AppAttachment;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AppAttachmentVO;
import com.ws.codecraft.monitor.MonitorContext;
import com.ws.codecraft.monitor.MonitorContextHolder;
import com.ws.codecraft.service.AppAttachmentService;
import com.ws.codecraft.service.AttachmentAnalysisService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * App attachment service implementation.
 */
@Service
@Slf4j
public class AppAttachmentServiceImpl extends ServiceImpl<AppAttachmentMapper, AppAttachment>
        implements AppAttachmentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final int MAX_CONTEXT_CHARS = 4500;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "csv", "html", "htm", "css", "js", "ts", "vue", "xml", "yml", "yaml"
    );

    @Resource
    private AppMapper appMapper;

    @Resource
    private CodeProjectProperties codeProjectProperties;

    @Resource
    private AttachmentAnalysisService attachmentAnalysisService;

    @Override
    public AppAttachmentVO uploadAttachment(Long appId, MultipartFile file, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "附件不能超过 10MB");
        App app = getOwnedApp(appId, loginUser);

        String originalFileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment");
        String extension = StrUtil.blankToDefault(FileUtil.extName(originalFileName), "").toLowerCase();
        String mimeType = file.getContentType();
        String fileType = detectFileType(extension, mimeType);
        ThrowUtils.throwIf(StrUtil.isBlank(fileType), ErrorCode.PARAMS_ERROR,
                "暂只支持图片、PDF、DOCX、TXT/MD/JSON/HTML/CSS/JS/TS/VUE 等文本附件");

        try {
            Path uploadDir = Path.of(codeProjectProperties.getUploadRootDir(),
                    String.valueOf(app.getId()), String.valueOf(loginUser.getId()));
            Files.createDirectories(uploadDir);
            String storedFileName = System.currentTimeMillis() + "_" + UUID.randomUUID() +
                    (StrUtil.isBlank(extension) ? "" : "." + extension);
            Path targetPath = uploadDir.resolve(storedFileName).normalize();
            file.transferTo(targetPath);

            AppAttachment attachment = new AppAttachment();
            attachment.setAppId(appId);
            attachment.setUserId(loginUser.getId());
            attachment.setFileName(originalFileName);
            attachment.setFileType(fileType);
            attachment.setMimeType(mimeType);
            attachment.setFileSize(file.getSize());
            attachment.setStoragePath(targetPath.toString());
            attachment.setParseStatus("processing");
            this.save(attachment);

            MonitorContextHolder.setContext(MonitorContext.builder()
                    .userId(String.valueOf(loginUser.getId()))
                    .appId(String.valueOf(appId))
                    .build());
            String parsedContent;
            try {
                parsedContent = attachmentAnalysisService.analyze(targetPath, originalFileName, fileType, mimeType);
            } finally {
                MonitorContextHolder.clearContext();
            }
            AppAttachment updateAttachment = new AppAttachment();
            updateAttachment.setId(attachment.getId());
            updateAttachment.setParsedContent(parsedContent);
            updateAttachment.setParseStatus("success");
            this.updateById(updateAttachment);

            attachment.setParsedContent(parsedContent);
            attachment.setParseStatus("success");
            return toVO(attachment);
        } catch (Exception e) {
            log.error("上传附件失败, appId={}, fileName={}, error={}", appId, originalFileName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传附件失败：" + e.getMessage());
        }
    }

    @Override
    public List<AppAttachmentVO> listAttachmentVO(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        getOwnedApp(appId, loginUser);
        return queryAppAttachments(appId, loginUser.getId()).stream().map(this::toVO).toList();
    }

    @Override
    public boolean deleteAttachment(Long attachmentId, User loginUser) {
        ThrowUtils.throwIf(attachmentId == null || attachmentId <= 0, ErrorCode.PARAMS_ERROR, "附件 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        AppAttachment attachment = this.getById(attachmentId);
        ThrowUtils.throwIf(attachment == null, ErrorCode.NOT_FOUND_ERROR, "附件不存在");
        getOwnedApp(attachment.getAppId(), loginUser);
        return this.removeById(attachmentId);
    }

    @Override
    public String buildAttachmentContext(Long appId, Long userId) {
        if (appId == null || userId == null) {
            return "";
        }
        List<AppAttachment> attachments = queryAppAttachments(appId, userId).stream()
                .filter(attachment -> "success".equals(attachment.getParseStatus()))
                .toList();
        if (attachments.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("用户上传附件分析摘要：\n");
        for (AppAttachment attachment : attachments) {
            if (StrUtil.isBlank(attachment.getParsedContent())) {
                continue;
            }
            builder.append("\n### ").append(attachment.getFileName())
                    .append("（").append(attachment.getFileType()).append("）\n")
                    .append(attachment.getParsedContent()).append("\n");
            if (builder.length() >= MAX_CONTEXT_CHARS) {
                builder.append("\n...（附件上下文已截断）");
                break;
            }
        }
        return StrUtil.subPre(builder.toString(), MAX_CONTEXT_CHARS);
    }

    private List<AppAttachment> queryAppAttachments(Long appId, Long userId) {
        return this.list(QueryWrapper.create()
                .eq("appId", appId)
                .eq("userId", userId)
                .orderBy("createTime", true));
    }

    private App getOwnedApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        App app = appMapper.selectOneById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        return app;
    }

    private String detectFileType(String extension, String mimeType) {
        String normalizedMimeType = StrUtil.blankToDefault(mimeType, "").toLowerCase();
        if (normalizedMimeType.startsWith("image/")) {
            return "image";
        }
        if ("pdf".equals(extension) || "application/pdf".equals(normalizedMimeType)) {
            return "pdf";
        }
        if ("docx".equals(extension)) {
            return "docx";
        }
        if (TEXT_EXTENSIONS.contains(extension) || normalizedMimeType.startsWith("text/")) {
            return "text";
        }
        return "";
    }

    private AppAttachmentVO toVO(AppAttachment attachment) {
        AppAttachmentVO vo = new AppAttachmentVO();
        BeanUtil.copyProperties(attachment, vo);
        return vo;
    }
}
