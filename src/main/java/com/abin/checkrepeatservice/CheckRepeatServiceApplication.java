package com.abin.checkrepeatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CheckRepeatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckRepeatServiceApplication.class, args);
    }

}
