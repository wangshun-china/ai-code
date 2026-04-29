package com.ws.codecraft.service;

import com.ws.codecraft.ai.AiCodeGeneratorServiceFactory;
import com.ws.codecraft.model.enums.AiModelEnum;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Converts uploaded files into concise prompt-ready attachment summaries.
 */
@Service
@Slf4j
public class AttachmentAnalysisService {

    private static final int MAX_TEXT_CHARS = 6000;
    private static final int MAX_SUMMARY_CHARS = 4000;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    public String analyze(Path filePath, String fileName, String fileType, String mimeType) {
        try {
            return switch (fileType) {
                case "image" -> analyzeImage(filePath, fileName, mimeType);
                case "pdf" -> analyzePdf(filePath, fileName, mimeType);
                case "docx" -> buildExtractedTextContext(fileName, extractDocxText(filePath));
                case "text" -> buildExtractedTextContext(fileName, Files.readString(filePath, StandardCharsets.UTF_8));
                default -> "附件 " + fileName + " 已上传，但当前类型暂不支持内容解析。";
            };
        } catch (Exception e) {
            log.warn("附件解析失败, fileName={}, error={}", fileName, e.getMessage(), e);
            return "附件 " + fileName + " 已上传，但解析失败：" + e.getMessage();
        }
    }

    private String analyzeImage(Path filePath, String fileName, String mimeType) throws IOException {
        String prompt = """
                请分析这张用户上传的设计稿/参考图，用于后续生成前端页面。
                输出中文摘要，必须包含：
                1. 页面/组件用途
                2. 布局结构
                3. 视觉风格、颜色、字体、间距
                4. 关键文案、图标、按钮、卡片等元素
                5. 生成 Vue 页面时需要注意的还原点
                文件名：%s
                """.formatted(fileName);
        return limitSummary(aiCodeGeneratorServiceFactory.chatWithImage(prompt, Files.readAllBytes(filePath),
                StrUtil.blankToDefault(mimeType, "image/png"), fileName, AiModelEnum.DEFAULT_MODEL_KEY));
    }

    private String analyzePdf(Path filePath, String fileName, String mimeType) throws IOException {
        String text = extractPdfText(filePath);
        if (StrUtil.isBlank(text)) {
            return "附件 " + fileName + " 是 PDF 文件，但未提取到可用文本。若这是扫描版简历或图片型 PDF，请转成图片上传。";
        }
        return buildExtractedTextContext(fileName, text);
    }

    private String extractPdfText(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String buildExtractedTextContext(String fileName, String text) {
        String normalized = StrUtil.blankToDefault(text, "").trim();
        if (normalized.length() > MAX_TEXT_CHARS) {
            normalized = normalized.substring(0, MAX_TEXT_CHARS);
        }
        if (StrUtil.isBlank(normalized)) {
            return "附件 " + fileName + " 内容为空或无法提取文本。";
        }
        return """
                以下是从附件 "%s" 中提取的文本内容。
                后续生成方案和代码时，请优先识别其中的关键信息；如果是简历，重点提取个人简介、技能、经历、项目、教育背景和适合展示的亮点。

                %s
                """.formatted(fileName, normalized);
    }

    private String extractDocxText(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    return xml
                            .replaceAll("<w:tab\\s*/>", "\t")
                            .replaceAll("</w:p>", "\n")
                            .replaceAll("<[^>]+>", "")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&");
                }
            }
        }
        return "";
    }

    private String limitSummary(String summary) {
        String normalized = StrUtil.blankToDefault(summary, "").trim();
        if (normalized.length() <= MAX_SUMMARY_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_CHARS) + "\n...（摘要已截断）";
    }
}
