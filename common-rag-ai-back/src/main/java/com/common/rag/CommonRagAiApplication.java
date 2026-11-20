package com.common.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CommonRagAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonRagAiApplication.class, args);
    }

}
