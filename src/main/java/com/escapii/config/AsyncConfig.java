package com.escapii.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Aktivira @Async u EmailService-u.
 * Emailovi se salju u zasebnom threadu i ne blokiraju HTTP response.
 * Thread pool je ogranicen kako bi se sprecilo thread exhaustion pod opterecenjem.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Glavni async executor - email slanje, notifikacije i ostalo.
     * PDF generisanje ide u zasebni pdfExecutor da ne blokira ove threadove.
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("escapii-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated thread pool za PDF generisanje.
     * Pool size = 3 (isto kao semafor) - threadovi se ne takmiče sa email threadovima.
     * Queue capacity = 20 - ako je 20+ vaučera istovremeno u redu, nešto je sigurno pošlo po krivu.
     */
    @Bean(name = "pdfExecutor")
    public Executor getPdfExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("escapii-pdf-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
            log.error("[Async] Neuhvaćena greška u {}: {}", method.getName(), throwable.getMessage(), throwable);
    }
}
