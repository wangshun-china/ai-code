package com.ws.codecraft.ai;

import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
