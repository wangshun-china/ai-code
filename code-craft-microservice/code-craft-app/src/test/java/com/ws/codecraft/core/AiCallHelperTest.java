package com.ws.codecraft.core;

import com.ws.codecraft.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiCallHelperTest {

    @Test
    void toFriendlyErrorMessageReturnsEmptyForNull() {
        assertEquals("", AiCallHelper.toFriendlyErrorMessage(null));
    }

    @Test
    void toFriendlyErrorMessageHandlesBusinessException() {
        assertEquals("业务异常信息", AiCallHelper.toFriendlyErrorMessage(
                new BusinessException(0, "业务异常信息")));
    }

    @Test
    void toFriendlyErrorMessageDetectsQuotaError() {
        String result = AiCallHelper.toFriendlyErrorMessage(
                new RuntimeException("AllocationQuota.FreeTierOnly"));
        assertTrue(result.contains("额度不足"));
    }

    @Test
    void toFriendlyErrorMessagePassesThroughGenericError() {
        String result = AiCallHelper.toFriendlyErrorMessage(
                new RuntimeException("网络超时"));
        assertEquals("网络超时", result);
    }

    @Test
    void isQuotaRelatedErrorDetectsFreeTierOnly() {
        assertTrue(AiCallHelper.isQuotaRelatedError("AllocationQuota.FreeTierOnly"));
    }

    @Test
    void isQuotaRelatedErrorDetectsInsufficientQuota() {
        assertTrue(AiCallHelper.isQuotaRelatedError("insufficient_quota"));
    }

    @Test
    void isQuotaRelatedErrorDetectsQuotaExceeded() {
        assertTrue(AiCallHelper.isQuotaRelatedError("quota has been exceeded"));
    }

    @Test
    void isQuotaRelatedErrorRejectsGeneric403() {
        assertFalse(AiCallHelper.isQuotaRelatedError("403 Forbidden"));
    }

    @Test
    void isQuotaRelatedErrorReturnsFalseForNull() {
        assertFalse(AiCallHelper.isQuotaRelatedError(null));
    }
}
