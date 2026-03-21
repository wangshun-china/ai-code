package com.ws.codecraft.service.impl;

import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.innerservice.InnerScreenshotService;
import com.ws.codecraft.service.ScreenshotService;
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
        log.info("========== [Dubbo服务] 截图服务被调用 ==========");
        log.info("调用方传入URL: {}", webUrl);
        try {
            String result = screenshotService.generateAndUploadScreenshot(webUrl);
            log.info("[Dubbo服务] 截图服务执行成功，返回URL: {}", result);
            return result;
        } catch (BusinessException e) {
            // 业务异常，直接抛出
            log.error("[Dubbo服务] 业务异常 - 错误码: {}, 消息: {}", e.getCode(), e.getMessage());
            throw new RuntimeException("截图服务调用失败: " + e.getMessage());
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // Playwright 特定异常
            log.error("[Dubbo服务] Playwright异常 - 这通常表示浏览器驱动未安装或系统依赖缺失");
            log.error("解决方案: 请执行 'playwright install chromium' 安装浏览器驱动");
            log.error("Playwright异常详情: ", e);
            throw new RuntimeException("截图服务调用失败(Playwright): " + e.getMessage() + " - 请检查浏览器驱动是否安装");
        } catch (Exception e) {
            // 其他异常
            log.error("[Dubbo服务] 截图服务执行失败 - URL: {}, 异常类型: {}, 消息: {}",
                    webUrl, e.getClass().getName(), e.getMessage());
            log.error("异常堆栈: ", e);
            throw new RuntimeException("截图服务调用失败: " + e.getMessage());
        }
    }
}