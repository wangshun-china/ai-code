package com.yupi.yuaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import com.yupi.yuaicodemother.exception.ThrowUtils;
import com.yupi.yuaicodemother.manager.CosManager;
import com.yupi.yuaicodemother.service.ScreenshotService;
import com.yupi.yuaicodemother.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        log.info("========== 截图服务开始 ==========");
        log.info("接收到的URL: {}", webUrl);

        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "截图的网址不能为空");
        log.info("参数校验通过");

        // 本地截图
        log.info("调用 WebScreenshotUtils 进行本地截图...");
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);

        if (StrUtil.isBlank(localScreenshotPath)) {
            log.error("【截图失败】本地截图返回路径为空，请检查 WebScreenshotUtils 日志获取详细错误信息");
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "生成网页截图失败 - 可能原因: 1)Playwright浏览器未安装 2)目标网页无法访问 3)网络超时。请查看详细日志");
        }
        log.info("本地截图成功，文件路径: {}", localScreenshotPath);

        // 上传图片到 COS
        try {
            log.info("开始上传截图到对象存储...");
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            if (StrUtil.isBlank(cosUrl)) {
                log.error("【截图失败】上传到对象存储返回URL为空");
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传截图到对象存储失败");
            }
            log.info("========== 截图服务完成 ==========");
            log.info("最终截图URL: {}", cosUrl);
            return cosUrl;
        } finally {
            // 清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 上传截图到对象存储
     *
     * @param localScreenshotPath 本地截图路径
     * @return 对象存储访问URL，失败返回null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键
     * 格式：/screenshots/2025/07/31/filename.jpg
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            FileUtil.del(localFile);
            log.info("清理本地文件成功: {}", localFilePath);
        }
    }
}
