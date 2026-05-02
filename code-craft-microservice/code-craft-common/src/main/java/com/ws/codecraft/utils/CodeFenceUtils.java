package com.ws.codecraft.utils;

import java.util.regex.Pattern;

/**
 * Utilities for cleaning Markdown code fences that models may mix into source files.
 */
public final class CodeFenceUtils {

    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^\\s*```[\\w.+-]*[ \\t]*\\R?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\R?\\s*```\\s*$");

    private CodeFenceUtils() {
    }

    /**
     * Removes a leading opening fence and/or a trailing closing fence.
     * This intentionally also handles truncated model output like "```html\n<html>...".
     */
    public static String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return null;
        }
        String cleaned = LEADING_CODE_FENCE.matcher(content).replaceFirst("");
        cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        return cleaned;
    }
}
