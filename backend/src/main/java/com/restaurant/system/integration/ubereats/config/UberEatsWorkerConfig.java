package com.restaurant.system.integration.ubereats.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name = "UBER_EATS_ENABLED", havingValue = "true")
public class UberEatsWorkerConfig {
    // Defining the dedicated scheduler makes Boot's scheduler auto-configuration back off.
    // Preserve the conventional default so ordinary print/outbox jobs never use the Uber thread.
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("restaurant-scheduled-");
        return scheduler;
    }

    @Bean("uberEatsScheduler")
    public ThreadPoolTaskScheduler scheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("uber-eats-");
        return scheduler;
    }
}
