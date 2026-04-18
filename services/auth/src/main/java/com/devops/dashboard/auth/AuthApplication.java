package com.devops.dashboard.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.devops.dashboard.shared.model")
@EnableJpaRepositories(basePackages = "com.devops.dashboard.shared.repository")
@ComponentScan(basePackages = {
    "com.devops.dashboard.auth",
    "com.devops.dashboard.shared.health",
    "com.devops.dashboard.shared.security",
    "com.devops.dashboard.shared.exception"
})
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
