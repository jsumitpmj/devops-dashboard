package com.devops.dashboard.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async processing so notifications can be delivered
 * in the background without blocking the caller.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
