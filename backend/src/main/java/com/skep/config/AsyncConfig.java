package com.skep.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @Async 전용 풀. 기본 SimpleAsyncTaskExecutor 는 매 호출마다 새 스레드를 만들어 OOM 위험.
 * verify-api 호출 폭주에도 backpressure 가 동작하도록 큐 + reject 정책.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("skep-async-");
        exec.setKeepAliveSeconds(60);
        // 큐 full + max pool 도달 시 호출 스레드가 직접 실행 — drop 방지.
        exec.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    /**
     * V82: 로컬 PaddleOCR(~90초 CPU) 만료일 백필 전용 풀.
     * 공유 taskExecutor(CallerRunsPolicy) 재사용 금지 — 90초 작업이 HTTP/이벤트 스레드를 점유하면 안 됨.
     * 포화 시 CallerRuns 대신 drop+로그(비차단) — 백필은 best-effort 라 유실 허용.
     */
    @Bean("ocrExecutor")
    public Executor ocrExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("skep-ocr-");
        exec.setKeepAliveSeconds(120);
        exec.setRejectedExecutionHandler((r, executor) ->
                log.warn("ocrExecutor 포화 — OCR 만료일 백필 작업 drop (queue/pool full)"));
        exec.initialize();
        return exec;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
