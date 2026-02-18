package com.peterscode.rentalmanagementsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // This enables @Async annotation for asynchronous audit logging
    // Spring will use default executor with reasonable thread pool settings
}
