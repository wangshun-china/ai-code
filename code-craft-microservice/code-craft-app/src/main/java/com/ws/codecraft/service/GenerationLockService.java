package com.ws.codecraft.service;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 应用代码生成互斥锁，避免同一应用并发写文件。
 */
@Service
public class GenerationLockService {

    private static final String LOCK_KEY_PREFIX = "code-craft:app:generation-lock:";
    private static final long LOCK_TTL_MINUTES = 30;

    @Resource
    private RedissonClient redissonClient;

    public String acquire(Long appId) {
        String lockToken = UUID.randomUUID().toString();
        RBucket<String> lockBucket = redissonClient.getBucket(LOCK_KEY_PREFIX + appId);
        boolean locked = lockBucket.setIfAbsent(lockToken, Duration.ofMinutes(LOCK_TTL_MINUTES));
        return locked ? lockToken : null;
    }

    public void release(Long appId, String lockToken) {
        if (appId == null || StrUtil.isBlank(lockToken)) {
            return;
        }
        RBucket<String> lockBucket = redissonClient.getBucket(LOCK_KEY_PREFIX + appId);
        String currentToken = lockBucket.get();
        if (lockToken.equals(currentToken)) {
            lockBucket.delete();
        }
    }
}
