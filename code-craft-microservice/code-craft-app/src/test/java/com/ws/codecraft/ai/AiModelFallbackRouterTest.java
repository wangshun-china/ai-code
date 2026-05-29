package com.ws.codecraft.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiModelFallbackRouterTest {

    private final AiModelFallbackRouter router = new AiModelFallbackRouter();

    @Test
    void resolveCandidatesIncludesPrimary() {
        List<String> candidates = router.resolveCandidates("qwen3.6-plus");
        assertEquals("qwen3.6-plus", candidates.get(0));
        assertTrue(candidates.size() > 1, "Should have fallback candidates");
    }

    @Test
    void resolveCandidatesNoDuplicates() {
        List<String> candidates = router.resolveCandidates("qwen3.6-plus");
        assertEquals(candidates.size(), candidates.stream().distinct().count(),
                "Candidates should not contain duplicates");
    }

    @Test
    void isQuotaExceededDetectsFreeTierOnly() {
        assertTrue(router.isQuotaExceeded(
                new RuntimeException("AllocationQuota.FreeTierOnly exceeded")));
    }

    @Test
    void isQuotaExceededDetectsInsufficientQuota() {
        assertTrue(router.isQuotaExceeded(
                new RuntimeException("insufficient_quota")));
    }

    @Test
    void isQuotaExceededDetectsQuotaExceeded() {
        assertTrue(router.isQuotaExceeded(
                new RuntimeException("Your quota has been exceeded for this model")));
    }

    @Test
    void isQuotaExceededRejectsGeneric403() {
        assertFalse(router.isQuotaExceeded(
                new RuntimeException("403 Forbidden")));
    }

    @Test
    void isQuotaExceededReturnsFalseForNull() {
        assertFalse(router.isQuotaExceeded(null));
    }

    @Test
    void isQuotaExceededReturnsFalseForUnrelatedError() {
        assertFalse(router.isQuotaExceeded(
                new RuntimeException("Connection timeout")));
    }
}
