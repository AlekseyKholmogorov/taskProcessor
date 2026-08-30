package com.example.tasks.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Общий PostgreSQL-контейнер для интеграционных тестов.
 */
public final class SharedPostgresContainer {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine3.24")
            .withDatabaseName("task_db")
            .withUsername("vertx")
            .withPassword("vertx_password")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("init.sql"),
                    "/docker-entrypoint-initdb.d/init.sql");

    static {
        POSTGRES.start();
    }

    private SharedPostgresContainer() {
    }

    /**
     * Возвращает запущенный контейнер PostgreSQL.
     *
     * @return контейнер
     */
    public static PostgreSQLContainer get() {
        return POSTGRES;
    }
}
