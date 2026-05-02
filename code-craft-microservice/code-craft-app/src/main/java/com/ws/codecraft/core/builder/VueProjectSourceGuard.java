package com.ws.codecraft.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs deterministic source fixes before handing the project to node-builder.
 */
@Slf4j
@Component
public class VueProjectSourceGuard {

    private static final Pattern RELATIVE_IMPORT_PATTERN = Pattern.compile(
            "(?<prefix>\\bfrom\\s*['\"]|\\bimport\\s*\\(\\s*['\"])(?<path>\\.{1,2}/[^'\"]+)(?<suffix>['\"])",
            Pattern.MULTILINE);

    private static final List<String> SOURCE_EXTENSIONS = List.of(".js", ".ts", ".vue");

    public List<String> repairMissingRelativeImports(Path projectRoot) {
        Path srcRoot = projectRoot.resolve("src").normalize();
        if (!Files.isDirectory(srcRoot)) {
            return List.of();
        }

        try {
            Map<String, List<Path>> fileNameIndex = buildFileNameIndex(srcRoot);
            List<Path> sourceFiles = listSourceFiles(srcRoot);
            List<String> repairs = new ArrayList<>();
            for (Path sourceFile : sourceFiles) {
                repairs.addAll(repairFileImports(srcRoot, sourceFile, fileNameIndex));
            }
            return repairs;
        } catch (IOException e) {
            log.warn("Vue 源码导入路径检查失败, projectRoot={}, error={}", projectRoot, e.getMessage(), e);
            return List.of();
        }
    }

    private Map<String, List<Path>> buildFileNameIndex(Path srcRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(srcRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .collect(Collectors.groupingBy(path -> path.getFileName().toString()));
        }
    }

    private List<Path> listSourceFiles(Path srcRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(srcRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isSourceFile)
                    .toList();
        }
    }

    private List<String> repairFileImports(Path srcRoot, Path sourceFile, Map<String, List<Path>> fileNameIndex)
            throws IOException {
        String content = Files.readString(sourceFile, StandardCharsets.UTF_8);
        Matcher matcher = RELATIVE_IMPORT_PATTERN.matcher(content);
        StringBuilder repairedContent = new StringBuilder();
        List<String> repairs = new ArrayList<>();
        boolean changed = false;

        while (matcher.find()) {
            String importPath = matcher.group("path");
            if (relativeImportExists(sourceFile.getParent(), importPath)) {
                continue;
            }

            String importedFileName = Path.of(importPath).getFileName().toString();
            Path targetFile = findUniqueTarget(fileNameIndex, importedFileName);
            if (targetFile == null) {
                continue;
            }

            String fixedImportPath = toRelativeImportPath(sourceFile.getParent(), targetFile);
            matcher.appendReplacement(repairedContent,
                    Matcher.quoteReplacement(matcher.group("prefix") + fixedImportPath + matcher.group("suffix")));
            changed = true;
            repairs.add("%s: %s -> %s".formatted(srcRoot.relativize(sourceFile), importPath, fixedImportPath));
        }

        if (!changed) {
            return repairs;
        }

        matcher.appendTail(repairedContent);
        Files.writeString(sourceFile, repairedContent.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
        return repairs;
    }

    private boolean relativeImportExists(Path importerDir, String importPath) {
        Path target = importerDir.resolve(importPath).normalize();
        if (Files.exists(target)) {
            return true;
        }
        if (hasKnownExtension(importPath)) {
            return false;
        }
        for (String extension : SOURCE_EXTENSIONS) {
            if (Files.exists(Path.of(target.toString() + extension))) {
                return true;
            }
        }
        return Files.exists(target.resolve("index.js")) || Files.exists(target.resolve("index.ts"));
    }

    private Path findUniqueTarget(Map<String, List<Path>> fileNameIndex, String importedFileName) {
        List<Path> candidates = fileNameIndex.get(importedFileName);
        if (candidates == null || candidates.size() != 1) {
            return null;
        }
        return candidates.get(0);
    }

    private String toRelativeImportPath(Path importerDir, Path targetFile) {
        String relativePath = importerDir.relativize(targetFile).toString().replace('\\', '/');
        return relativePath.startsWith(".") ? relativePath : "./" + relativePath;
    }

    private boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SOURCE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    private boolean hasKnownExtension(String importPath) {
        String lowerPath = importPath.toLowerCase(Locale.ROOT);
        return SOURCE_EXTENSIONS.stream().anyMatch(lowerPath::endsWith)
                || lowerPath.endsWith(".css")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".svg")
                || lowerPath.endsWith(".png")
                || lowerPath.endsWith(".jpg")
                || lowerPath.endsWith(".jpeg");
    }
}
