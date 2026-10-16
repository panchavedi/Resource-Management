package com.talent.server.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplies a real PostgreSQL database to integration tests.
 * <ol>
 *   <li>If {@code TEST_DB_URL} is set (environment variable or the project's {@code .env}), that database is
 *       used with {@code TEST_DB_USERNAME}/{@code TEST_DB_PASSWORD}, falling back to {@code DB_USERNAME}/{@code DB_PASSWORD}.</li>
 *   <li>Otherwise a disposable PostgreSQL container is started with Testcontainers (requires Docker).</li>
 * </ol>
 * Flyway creates the schema in both cases; tests truncate the tables between runs.
 */
public final class TestDatabase {

    private static PostgreSQLContainer container;

    private TestDatabase() {
    }

    public static synchronized void register(DynamicPropertyRegistry registry) {
        Map<String, String> env = settings();
        String url = env.get("TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username",
                    () -> env.getOrDefault("TEST_DB_USERNAME", env.getOrDefault("DB_USERNAME", "postgres")));
            registry.add("spring.datasource.password",
                    () -> env.getOrDefault("TEST_DB_PASSWORD", env.getOrDefault("DB_PASSWORD", "")));
            return;
        }
        if (container == null) {
            container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    /** Values from {@code ../.env} or {@code ./.env}, overridden by real environment variables. */
    private static Map<String, String> settings() {
        Map<String, String> values = new HashMap<>();
        for (Path candidate : new Path[]{Path.of("..", ".env"), Path.of(".env")}) {
            if (Files.isRegularFile(candidate)) {
                try {
                    for (String line : Files.readAllLines(candidate)) {
                        String trimmed = line.trim();
                        int eq = trimmed.indexOf('=');
                        if (!trimmed.startsWith("#") && eq > 0) {
                            values.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read " + candidate, e);
                }
            }
        }
        System.getenv().forEach(values::put);
        return values;
    }
}
