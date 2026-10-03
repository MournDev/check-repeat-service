package com.abin.checkrepeatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
// 缺这个注解时所有 @Scheduled 方法都不会执行——包括 AutoBackupScheduler，
// 以及一直处于"写了但没跑"状态的 CheckTaskScheduler / DataIntegrityChecker
@EnableScheduling
public class CheckRepeatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckRepeatServiceApplication.class, args);
    }

}
