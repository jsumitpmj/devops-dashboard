package com.devops.dashboard.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.devops.dashboard.shared.model")
@EnableJpaRepositories(basePackages = "com.devops.dashboard.shared.repository")
@ComponentScan(basePackages = {
    "com.devops.dashboard.notification",
    "com.devops.dashboard.shared.health",
    "com.devops.dashboard.shared.security",
    "com.devops.dashboard.shared.exception"
})
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
