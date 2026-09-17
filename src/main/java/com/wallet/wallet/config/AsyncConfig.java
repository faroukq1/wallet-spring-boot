package com.wallet.wallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Spring Boot 3+ auto-configures a default task executor.
    // For production, define a ThreadPoolTaskExecutor bean here with a bounded queue.
}