package com.ws.codecraft.model.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiModelRegistryTest {

    @Test
    void normalizeReturnsDefaultForNull() {
        assertEquals(AiModelRegistry.DEFAULT_MODEL_KEY, AiModelRegistry.normalize(null));
    }

    @Test
    void normalizeReturnsDefaultForBlank() {
        assertEquals(AiModelRegistry.DEFAULT_MODEL_KEY, AiModelRegistry.normalize("   "));
    }

    @Test
    void normalizeTrimsValue() {
        assertEquals("qwen-plus", AiModelRegistry.normalize("  qwen-plus  "));
    }

    @Test
    void registerAndGetDynamicModel() {
        AiModelRegistry.registerDynamicModel("test-key-1", "Test Model", true,
                "sk-test", "https://api.test.com", "test-model-v1");

        assertEquals("sk-test", AiModelRegistry.getDynamicApiKey("test-key-1"));
        assertEquals("https://api.test.com", AiModelRegistry.getDynamicBaseUrl("test-key-1"));
        assertEquals("test-model-v1", AiModelRegistry.getActualModelName("test-key-1"));
        assertTrue(AiModelRegistry.isDynamicModel("test-key-1"));

        AiModelRegistry.removeDynamicModelsByPrefix("test-key-");
        assertFalse(AiModelRegistry.isDynamicModel("test-key-1"));
    }

    @Test
    void getActualModelNameFallsBackToKeyWhenNotDynamic() {
        String result = AiModelRegistry.getActualModelName("qwen3.6-plus");
        assertEquals("qwen3.6-plus", result);
    }

    @Test
    void removeByPrefixDoesNothingForNull() {
        assertDoesNotThrow(() -> AiModelRegistry.removeDynamicModelsByPrefix(null));
    }

    @Test
    void getDynamicApiKeyReturnsNullForUnknown() {
        assertNull(AiModelRegistry.getDynamicApiKey("nonexistent-key"));
    }
}
