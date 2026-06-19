package com.loom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LoomApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoomApplication.class, args);
    }
}
