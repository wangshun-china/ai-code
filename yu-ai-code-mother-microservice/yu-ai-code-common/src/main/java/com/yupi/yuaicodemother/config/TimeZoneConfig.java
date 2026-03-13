package com.yupi.yuaicodemother.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 时区配置
 * 设置 JVM 默认时区为北京时间（Asia/Shanghai）
 */
@Configuration
public class TimeZoneConfig {

    private static final String BEIJING_TIMEZONE = "Asia/Shanghai";

    @PostConstruct
    public void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BEIJING_TIMEZONE));
        System.setProperty("user.timezone", BEIJING_TIMEZONE);
        System.out.println("========================================");
        System.out.println("时区配置完成: " + BEIJING_TIMEZONE);
        System.out.println("当前时区: " + TimeZone.getDefault().getID());
        System.out.println("ZoneId: " + ZoneId.systemDefault());
        System.out.println("========================================");
    }
}