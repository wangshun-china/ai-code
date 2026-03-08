package com.yupi.yuaicodemother.service.impl;

import com.yupi.yuaicodemother.innerservice.InnerScreenshotService;
import com.yupi.yuaicodemother.service.ScreenshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
@Slf4j
@DubboService
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    @Resource
    private ScreenshotService screenshotService;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        try {
        return screenshotService.generateAndUploadScreenshot(webUrl);
        } catch (Exception e) {
            // 🔥 关键点：捕获所有异常，打印日志，并转换成简单的 RuntimeException
            // 这样做可以剥离掉可能导致序列化失败的复杂异常对象
            log.error("截图服务执行失败, URL: {}", webUrl, e);
            throw new RuntimeException("截图服务调用失败: " + e.getMessage());
        }
    }
}