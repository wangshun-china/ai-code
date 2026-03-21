package com.ws.codecraft;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class CodeCraftScreenshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeCraftScreenshotApplication.class, args);
    }
}