package com.ws.codecraftuser;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDubbo
@MapperScan("com.ws.codecraftuser.mapper")
@ComponentScan("com.ws")
public class CodeCraftUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeCraftUserApplication.class, args);
    }
}