package com.yupi.yuaicodemother.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
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
            log.info("浏览器已存在，跳过初始化");
            return;
        }
        log.info("========== 开始初始化 Playwright 浏览器 ==========");
        try {
            log.info("步骤1: 创建 Playwright 实例...");
            playwright = Playwright.create();
            log.info("Playwright 实例创建成功");

            log.info("步骤2: 启动 Chromium 浏览器 (headless模式)...");
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
            );
            log.info("========== Playwright 浏览器初始化成功 ==========");
            log.info("浏览器版本: {}", browser.version());
        } catch (com.microsoft.playwright.PlaywrightException e) {
            log.error("【初始化失败】Playwright异常 - 可能原因: 浏览器驱动未安装");
            log.error("请执行命令安装浏览器: mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args=\"install chromium\"");
            log.error("Playwright异常详情: ", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Playwright 浏览器初始化失败，请先安装浏览器驱动: " + e.getMessage());
        } catch (Exception e) {
            log.error("【初始化失败】未知异常 - 类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);
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
        log.info("========== 开始网页截图流程 ==========");
        log.info("目标URL: {}", webUrl);
        log.info("操作系统: {}, 临时目录: {}", System.getProperty("os.name"), TEMP_DIR_PATH);

        // 非空校验
        if (StrUtil.isBlank(webUrl)) {
            log.error("【截图失败】URL为空");
            return null;
        }

        // Docker 环境下替换 localhost 为 nginx 容器名
        String originalUrl = webUrl;
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
        log.info("检查浏览器初始化状态...");
        if (browser == null) {
            log.warn("浏览器未初始化，尝试初始化...");
            try {
                initBrowser();
            } catch (Exception e) {
                log.error("【截图失败】浏览器初始化异常 - 类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);
                return null;
            }
        }
        log.info("浏览器状态: {}", browser != null ? "已初始化" : "初始化失败");

        // 创建临时目录
        String rootPath = TEMP_DIR_PATH + UUID.randomUUID().toString().substring(0, 8);
        try {
            FileUtil.mkdir(rootPath);
            log.info("临时目录创建成功: {}", rootPath);
        } catch (Exception e) {
            log.error("【截图失败】临时目录创建失败 - 路径: {}, 错误: {}", rootPath, e.getMessage(), e);
            return null;
        }

        Page page = null;
        try {
            // 创建新页面（设置视口大小）
            log.info("创建浏览器页面...");
            page = browser.newPage(new Browser.NewPageOptions()
                    .setViewportSize(SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT));
            log.info("页面创建成功，视口大小: {}x{}", SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT);

            // 设置超时
            page.setDefaultTimeout(PAGE_TIMEOUT);
            log.info("页面超时设置: {}ms", PAGE_TIMEOUT);

            // 访问网页
            log.info("开始访问网页: {}", webUrl);
            long navigateStart = System.currentTimeMillis();
            page.navigate(webUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.NETWORKIDLE));
            log.info("网页访问成功，耗时: {}ms", System.currentTimeMillis() - navigateStart);

            // 等待页面加载
            log.info("等待页面渲染...");
            page.waitForTimeout(2000);
            log.info("页面渲染等待完成");

            // 截图
            log.info("开始截图...");
            byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(false)
                    .setType(ScreenshotType.PNG));
            log.info("截图成功，图片大小: {} bytes", screenshotBytes.length);

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

            log.info("========== 网页截图流程完成 ==========");
            return compressedImagePath;
        } catch (com.microsoft.playwright.PlaywrightException e) {
            log.error("【截图失败】Playwright异常 - URL: {}, 错误类型: {}, 消息: {}",
                    originalUrl, e.getClass().getSimpleName(), e.getMessage());
            log.error("Playwright异常详情: ", e);
            return null;
        } catch (Exception e) {
            log.error("【截图失败】未知异常 - URL: {}, 异常类型: {}, 消息: {}",
                    originalUrl, e.getClass().getName(), e.getMessage());
            log.error("异常详情: ", e);
            return null;
        } finally {
            // 关闭页面，释放资源
            if (page != null) {
                try {
                    page.close();
                    log.info("浏览器页面已关闭");
                } catch (Exception e) {
                    log.warn("关闭页面失败: {}", e.getMessage());
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