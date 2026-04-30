package com.ws.codecraft.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight local RAG over code generation templates.
 */
@Service
@Slf4j
public class CodegenTemplateRagService {

    private static final String TEMPLATE_RESOURCE = "rag/codegen-templates.json";

    private final List<TemplateEntry> templates = new ArrayList<>();

    @PostConstruct
    public void loadTemplates() {
        try (InputStream inputStream = new ClassPathResource(TEMPLATE_RESOURCE).getInputStream()) {
            String json = IoUtil.read(inputStream, StandardCharsets.UTF_8);
            JSONArray array = JSONUtil.parseArray(json);
            templates.clear();
            for (Object item : array) {
                JSONObject object = JSONUtil.parseObj(item);
                TemplateEntry entry = new TemplateEntry();
                entry.setId(object.getStr("id"));
                entry.setTitle(object.getStr("title"));
                entry.setCodeGenTypes(readStringList(object, "codeGenTypes"));
                entry.setKeywords(readStringList(object, "keywords"));
                entry.setScenario(object.getStr("scenario"));
                entry.setImplementationNotes(readStringList(object, "implementationNotes"));
                entry.setFileHints(readStringList(object, "fileHints"));
                entry.setQualityChecklist(readStringList(object, "qualityChecklist"));
                templates.add(entry);
            }
            log.info("代码生成模板库加载完成, count={}", templates.size());
        } catch (Exception e) {
            log.warn("代码生成模板库加载失败，将跳过模板检索: {}", e.getMessage());
            templates.clear();
        }
    }

    public TemplateRetrievalResult retrieve(String userMessage, String codeGenType, int limit) {
        if (CollUtil.isEmpty(templates) || StrUtil.isBlank(userMessage)) {
            return TemplateRetrievalResult.empty();
        }
        String normalizedMessage = userMessage.toLowerCase();
        String normalizedCodeGenType = StrUtil.blankToDefault(codeGenType, "").toLowerCase();
        List<ScoredTemplate> matches = templates.stream()
                .map(template -> new ScoredTemplate(template, score(template, normalizedMessage, normalizedCodeGenType)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredTemplate::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
        if (matches.isEmpty()) {
            return TemplateRetrievalResult.empty();
        }
        List<String> titles = matches.stream().map(match -> match.template().getTitle()).toList();
        return new TemplateRetrievalResult(formatContext(matches), titles);
    }

    private int score(TemplateEntry template, String message, String codeGenType) {
        int score = 0;
        if (template.getCodeGenTypes().stream().anyMatch(type -> type.equalsIgnoreCase(codeGenType))) {
            score += 5;
        }
        for (String keyword : template.getKeywords()) {
            if (StrUtil.isNotBlank(keyword) && message.contains(keyword.toLowerCase())) {
                score += 3;
            }
        }
        return score;
    }

    private String formatContext(List<ScoredTemplate> matches) {
        StringBuilder builder = new StringBuilder("代码生成模板检索结果（RAG 上下文，仅作参考）：\n");
        for (ScoredTemplate match : matches) {
            TemplateEntry template = match.template();
            builder.append("\n### ").append(template.getTitle()).append('\n')
                    .append("适用场景：").append(template.getScenario()).append('\n')
                    .append("实现要点：\n");
            appendList(builder, template.getImplementationNotes());
            builder.append("文件建议：\n");
            appendList(builder, template.getFileHints());
            builder.append("质量检查：\n");
            appendList(builder, template.getQualityChecklist());
        }
        return builder.toString();
    }

    private void appendList(StringBuilder builder, List<String> values) {
        for (String value : values) {
            builder.append("- ").append(value).append('\n');
        }
    }

    private List<String> readStringList(JSONObject object, String key) {
        JSONArray array = object.getJSONArray(key);
        if (array == null) {
            return List.of();
        }
        return array.stream()
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    public record TemplateRetrievalResult(String context, List<String> templateTitles) {

        public static TemplateRetrievalResult empty() {
            return new TemplateRetrievalResult("", List.of());
        }
    }

    private record ScoredTemplate(TemplateEntry template, int score) {
    }

    @Data
    private static class TemplateEntry {

        private String id;

        private String title;

        private List<String> codeGenTypes = List.of();

        private List<String> keywords = List.of();

        private String scenario;

        private List<String> implementationNotes = List.of();

        private List<String> fileHints = List.of();

        private List<String> qualityChecklist = List.of();
    }
}
