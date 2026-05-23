package com.ws.codecraft.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 时区配置
 * 设置 JVM 默认时区为北京时间（Asia/Shanghai）
 */
@Configuration
@Slf4j
public class TimeZoneConfig {

    private static final String BEIJING_TIMEZONE = "Asia/Shanghai";

    @PostConstruct
    public void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BEIJING_TIMEZONE));
        System.setProperty("user.timezone", BEIJING_TIMEZONE);
        log.info("时区配置完成: {}, 当前时区: {}, ZoneId: {}",
                BEIJING_TIMEZONE, TimeZone.getDefault().getID(), ZoneId.systemDefault());
    }
}
