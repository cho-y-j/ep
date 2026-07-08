package com.skep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync       // S-4 단계 3: 자동 OCR/검증 트리거를 비동기로 실행
@EnableScheduling  // S-12: WorksheetEditorService 24h cleanup cron
public class SkepApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkepApplication.class, args);
    }
}
