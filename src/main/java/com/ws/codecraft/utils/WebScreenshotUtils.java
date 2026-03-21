package com.ws.codecraft.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

/**
 * 截图工具类 - 支持多环境自动切换
 *
 * 根据操作系统和Spring Profile自动选择配置：
 * 1. Windows系统 + local profile: 使用WebDriverManager，相对路径
 * 2. Linux/Docker系统 + docker profile: 使用系统Chrome，/tmp绝对路径
 */
@Slf4j
public class WebScreenshotUtils {

    private static final WebDriver webDriver;

    // 环境检测
    private static final boolean IS_WINDOWS;
    private static final boolean IS_LOCAL_PROFILE;
    private static final String TEMP_DIR_PATH;

    static {
        // 检测操作系统
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_WINDOWS = osName.contains("win");

        // 检测Spring Profile
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null) {
            activeProfile = System.getProperty("spring.profiles.active", "local");
        }
        IS_LOCAL_PROFILE = "local".equalsIgnoreCase(activeProfile);

        // 根据环境选择临时目录路径
        if (IS_WINDOWS && IS_LOCAL_PROFILE) {
            // Windows本地开发：使用项目相对路径
            TEMP_DIR_PATH = System.getProperty("user.dir") + "/tmp/screenshots/";
        } else {
            // Linux/Docker环境：使用系统绝对路径
            TEMP_DIR_PATH = "/tmp/screenshots/";
        }

        log.info("WebScreenshotUtils初始化 - 操作系统: {}, Spring Profile: {}, 临时目录: {}",
                osName, activeProfile, TEMP_DIR_PATH);

        // 初始化Chrome驱动
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 退出时销毁
     */
    @PreDestroy
    public void destroy() {
        webDriver.quit();
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
        // 创建临时目录
        try {
            String rootPath = TEMP_DIR_PATH + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始图片保存路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            // 访问网页
            webDriver.get(webUrl);
            // 等待网页加载
            waitForPageLoad(webDriver);
            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);
            // 压缩图片
            final String COMPRESS_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESS_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);
            // 删除原始图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败：{}", webUrl, e);
            return null;
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 根据环境选择初始化策略
            if (IS_WINDOWS && IS_LOCAL_PROFILE) {
                return initChromeDriverForWindows(width, height);
            } else {
                return initChromeDriverForLinux(width, height);
            }
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * Windows本地开发环境初始化
     */
    private static WebDriver initChromeDriverForWindows(int width, int height) {
        log.info("使用Windows本地开发环境配置初始化Chrome驱动");

        try {
            // 自动管理 ChromeDriver
            WebDriverManager.chromedriver().setup();
        } catch (Exception e) {
            log.warn("WebDriverManager自动管理ChromeDriver失败，尝试使用系统PATH中的ChromeDriver", e);
            // 如果WebDriverManager失败，尝试使用系统PATH中的ChromeDriver
            System.setProperty("webdriver.chrome.driver", "chromedriver");
        }

        // 配置 Chrome 选项
        ChromeOptions options = new ChromeOptions();
        // 无头模式
        options.addArguments("--headless");
        // 禁用GPU（在某些环境下避免问题）
        options.addArguments("--disable-gpu");
        // 禁用沙盒模式（Docker环境需要）
        options.addArguments("--no-sandbox");
        // 禁用开发者shm使用
        options.addArguments("--disable-dev-shm-usage");
        // 设置窗口大小
        options.addArguments(String.format("--window-size=%d,%d", width, height));
        // 禁用扩展
        options.addArguments("--disable-extensions");
        // 设置用户代理
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

        // 创建驱动
        WebDriver driver = new ChromeDriver(options);
        // 设置页面加载超时
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        // 设置隐式等待
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        return driver;
    }

    /**
     * Linux/Docker环境初始化
     */
    private static WebDriver initChromeDriverForLinux(int width, int height) {
        log.info("使用Linux/Docker环境配置初始化Chrome驱动");

        try {
            // Docker 环境下直接使用系统安装的 Chrome
            // 设置 Chrome 二进制路径
            System.setProperty("webdriver.chrome.binary", "/usr/bin/google-chrome");
        } catch (Exception e) {
            log.warn("设置Chrome二进制路径失败，尝试使用默认路径", e);
            // 如果路径设置失败，尝试使用系统PATH中的Chrome
        }

        // 配置 Chrome 选项
        ChromeOptions options = new ChromeOptions();
        try {
            // 设置 Chrome 二进制路径（备用方法）
            options.setBinary("/usr/bin/google-chrome");
        } catch (Exception e) {
            log.warn("通过ChromeOptions设置二进制路径失败，使用系统默认Chrome", e);
        }
        // 无头模式
        options.addArguments("--headless");
        // 禁用GPU（在某些环境下避免问题）
        options.addArguments("--disable-gpu");
        // 禁用沙盒模式（Docker环境必须）
        options.addArguments("--no-sandbox");
        // 禁用开发者shm使用（Docker环境必须）
        options.addArguments("--disable-dev-shm-usage");
        // 设置窗口大小
        options.addArguments(String.format("--window-size=%d,%d", width, height));
        // 禁用扩展
        options.addArguments("--disable-extensions");
        // 禁用软件渲染列表
        options.addArguments("--disable-software-rasterizer");
        // 禁用加速2D canvas
        options.addArguments("--disable-accelerated-2d-canvas");
        // 禁用加速视频解码
        options.addArguments("--disable-accelerated-video-decode");
        // 禁用音频输出
        options.addArguments("--disable-audio-output");
        // 设置用户代理
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        // 远程调试端口（可选）
        options.addArguments("--remote-debugging-port=9222");
        // 禁用崩溃报告
        options.addArguments("--disable-crash-reporter");
        // 禁用错误弹窗
        options.addArguments("--disable-error-dialogs");

        // 创建驱动
        WebDriver driver = new ChromeDriver(options);
        // 设置页面加载超时
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        // 设置隐式等待
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        return driver;
    }

    /**
     * 保存图片到文件
     *
     * @param imageBytes
     * @param imagePath
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
     *
     * @param originImagePath
     * @param compressedImagePath
     */
    private static void compressImage(String originImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
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
     * 等待页面加载完成
     *
     * @param webDriver
     */
    private static void waitForPageLoad(WebDriver webDriver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            // 等待 document.readyState 为 complete
            wait.until(driver -> ((JavascriptExecutor) driver)
                    .executeScript("return document.readyState").
                    equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * 获取当前环境信息（用于调试）
     */
    public static String getEnvironmentInfo() {
        return String.format("操作系统: %s, Spring Profile: %s, 临时目录: %s",
                System.getProperty("os.name"),
                System.getenv("SPRING_PROFILES_ACTIVE"),
                TEMP_DIR_PATH);
    }
}
