package com.ws.codecraft.ai;

import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class AiCodeGenTypeRoutingServiceFactoryTest {

    private final AiCodeGenTypeRoutingServiceFactory factory = new AiCodeGenTypeRoutingServiceFactory();

    @Test
    void routeExplicitHtmlPromptWithoutModelCall() {
        CodeGenTypeEnum result = ReflectionTestUtils.invokeMethod(
                factory,
                "routeByExplicitUserIntent",
                "生成一个简单的html"
        );

        assertEquals(CodeGenTypeEnum.HTML, result);
    }

    @Test
    void routeExplicitVuePromptBeforeHtmlKeyword() {
        CodeGenTypeEnum result = ReflectionTestUtils.invokeMethod(
                factory,
                "routeByExplicitUserIntent",
                "生成一个简单的 Vue 项目，不要写成普通 html"
        );

        assertEquals(CodeGenTypeEnum.VUE_PROJECT, result);
    }

    @Test
    void fallbackParsingRecognizesAllTypes() {
        CodeGenTypeEnum html = ReflectionTestUtils.invokeMethod(factory, "parseCodeGenTypeFallback", "HTML");
        assertEquals(CodeGenTypeEnum.HTML, html);

        CodeGenTypeEnum multi = ReflectionTestUtils.invokeMethod(factory, "parseCodeGenTypeFallback", "MULTI_FILE");
        assertEquals(CodeGenTypeEnum.MULTI_FILE, multi);

        CodeGenTypeEnum vue = ReflectionTestUtils.invokeMethod(factory, "parseCodeGenTypeFallback", "VUE_PROJECT");
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, vue);
    }

    @Test
    void fallbackParsingDefaultsToVueProject() {
        CodeGenTypeEnum result = ReflectionTestUtils.invokeMethod(factory, "parseCodeGenTypeFallback", "UNKNOWN_TYPE");
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, result);
    }

    @Test
    void fallbackParsingHandlesMixedCase() {
        CodeGenTypeEnum result = ReflectionTestUtils.invokeMethod(factory, "parseCodeGenTypeFallback", "vue_project");
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, result);
    }
}
