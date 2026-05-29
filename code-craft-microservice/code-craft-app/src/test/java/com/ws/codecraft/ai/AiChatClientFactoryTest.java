package com.ws.codecraft.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiChatClientFactoryTest {

    @Test
    void dashscopeBaseUrlIsConsistent() {
        assertEquals(UserAiConfigManager.DEFAULT_BASE_URL,
                AiChatClientFactory.DASHSCOPE_COMPATIBLE_BASE_URL,
                "Base URL constant should be shared between factory and config manager");
    }
}
