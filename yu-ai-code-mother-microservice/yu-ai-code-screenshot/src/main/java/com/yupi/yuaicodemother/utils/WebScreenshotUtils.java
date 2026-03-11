package com.yupi.yuaicodemother.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.yupi.yuaicodemother.exception.BusinessException;
import com.yupi.yuaicodemother.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.UUID;

/**
 * 截图工具类 - 基于 Playwright（比 Selenium 更轻量）
 */
@Slf4j
public class WebScreenshotUtils {

    private static Playwright playwright;
    private static Browser browser;

    // 环境检测
    private static final boolean IS_WINDOWS;
    private static final String TEMP_DIR_PATH;

    // 截图超时时间（毫秒）
    private static final int PAGE_TIMEOUT = 30000;
    private static final int SCREENSHOT_WIDTH = 1600;
    private static final int SCREENSHOT_HEIGHT = 900;

    static {
        // 检测操作系统
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_WINDOWS = osName.contains("win");

        // 根据环境选择临时目录路径
        if (IS_WINDOWS) {
            TEMP_DIR_PATH = System.getProperty("user.dir") + "/tmp/screenshots/";
        } else {
            TEMP_DIR_PATH = "/tmp/screenshots/";
        }

        log.info("WebScreenshotUtils初始化 - 操作系统: {}, 临时目录: {}", osName, TEMP_DIR_PATH);

        // 初始化 Playwright（延迟初始化，首次使用时才启动浏览器）
        initBrowser();
    }

    /**
     * 初始化 Playwright 浏览器
     */
    private static synchronized void initBrowser() {
        if (browser != null) {
            return;
        }
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new Browser.NewContextOptions()
                            .setViewportSize(SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT)
            );
            log.info("Playwright 浏览器初始化成功");
        } catch (Exception e) {
            log.error("Playwright 浏览器初始化失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Playwright 浏览器初始化失败: " + e.getMessage());
        }
    }

    /**
     * 退出时销毁
     */
    @PreDestroy
    public void destroy() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        log.info("Playwright 资源已释放");
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 要截图的网址
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        // 非空校验
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页截图失败，url为空");
            return null;
        }

        // Docker 环境下替换 localhost 为 nginx 容器名
        if (!IS_WINDOWS) {
            if (webUrl.contains("localhost")) {
                webUrl = webUrl.replace("localhost", "yu-ai-nginx");
                log.info("Docker环境检测：已将目标地址修正为内部网络地址 -> {}", webUrl);
            } else if (webUrl.contains("127.0.0.1")) {
                webUrl = webUrl.replace("127.0.0.1", "yu-ai-nginx");
                log.info("Docker环境检测：已将目标地址修正为内部网络地址 -> {}", webUrl);
            }
        }

        // 确保浏览器已初始化
        if (browser == null) {
            initBrowser();
        }

        // 创建临时目录
        String rootPath = TEMP_DIR_PATH + UUID.randomUUID().toString().substring(0, 8);
        FileUtil.mkdir(rootPath);

        Page page = null;
        try {
            // 创建新页面
            page = browser.newPage();

            // 设置超时
            page.setDefaultTimeout(PAGE_TIMEOUT);

            // 访问网页
            log.info("正在访问网页: {}", webUrl);
            page.navigate(webUrl, new Page.NavigateOptions().setWaitUntil("networkidle"));

            // 等待页面加载
            page.waitForTimeout(2000);

            // 截图
            byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(false)
                    .setType(Page.ScreenshotType.PNG));

            // 保存原始图片
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + ".png";
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);

            // 压缩图片
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + "_compressed.jpg";
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);

            // 删除原始图片
            FileUtil.del(imageSavePath);

            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败：{}", webUrl, e);
            return null;
        } finally {
            // 关闭页面，释放资源
            if (page != null) {
                try {
                    page.close();
                } catch (Exception e) {
                    log.warn("关闭页面失败", e);
                }
            }
        }
    }

    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originImagePath, String compressedImagePath) {
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败：{} -> {}", originImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 获取当前环境信息（用于调试）
     */
    public static String getEnvironmentInfo() {
        return String.format("操作系统: %s, 临时目录: %s, 浏览器状态: %s",
                System.getProperty("os.name"),
                TEMP_DIR_PATH,
                browser != null ? "已初始化" : "未初始化");
    }
}