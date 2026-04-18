package com.devops.dashboard.shared.health;

import com.devops.dashboard.shared.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Reusable health check logic shared across all services.
 * Each service auto-wires this and exposes it via a /health endpoint.
 */
@Component
public class HealthChecker {

    private final DataSource dataSource;
    private final long startTime = System.currentTimeMillis();

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Value("${app.version:1.0.0}")
    private String version;

    public HealthChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthResponse check() {
        HealthResponse.DatabaseHealth dbHealth = checkDatabase();

        return HealthResponse.builder()
                .status(dbHealth.isOk() ? "ok" : "degraded")
                .service(serviceName)
                .version(version)
                .uptime((System.currentTimeMillis() - startTime) / 1000)
                .timestamp(OffsetDateTime.now().toString())
                .database(dbHealth)
                .build();
    }

    private HealthResponse.DatabaseHealth checkDatabase() {
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            return HealthResponse.DatabaseHealth.builder()
                    .ok(true)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return HealthResponse.DatabaseHealth.builder()
                    .ok(false)
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }
}
