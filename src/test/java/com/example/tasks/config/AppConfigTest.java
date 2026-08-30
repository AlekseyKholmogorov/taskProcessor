package com.example.tasks.config;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    void usesDefaultsWhenConfigIsEmpty() {
        AppConfig config = new AppConfig(new JsonObject());

        assertEquals(8080, config.httpPort());
        assertEquals("/api/tasks", config.apiTasksPath());
        assertEquals("/ws/tasks/", config.wsPathPrefix());
        assertEquals("localhost", config.dbHost());
        assertEquals(5432, config.dbPort());
        assertEquals("task_db", config.dbName());
        assertEquals("vertx", config.dbUser());
        assertEquals(5, config.dbPoolMaxSize());
        assertEquals("task.start", config.taskStartAddress());
        assertEquals("task.progress", config.taskProgressAddress());
        assertEquals(1000L, config.tickIntervalMs());
        assertEquals(20, config.progressStep());
    }

    @Test
    void treatsNullRawConfigAsEmpty() {
        AppConfig config = new AppConfig(null);

        assertEquals(8080, config.httpPort());
        assertEquals("localhost", config.dbHost());
    }

    @Test
    void readsOverridesFromJson() {
        JsonObject raw = new JsonObject()
                .put("HTTP_PORT", 9090)
                .put("HTTP_API_TASKS_PATH", "/v1/tasks")
                .put("WS_PATH_PREFIX", "/ws/")
                .put("DB_HOST", "db.internal")
                .put("DB_PORT", "5433")
                .put("DB_NAME", "custom_db")
                .put("DB_USER", "app")
                .put("DB_PASSWORD", "secret")
                .put("DB_POOL_MAX_SIZE", 12)
                .put("EVENTBUS_TASK_START", "custom.start")
                .put("EVENTBUS_TASK_PROGRESS", "custom.progress")
                .put("TASK_TICK_INTERVAL_MS", 50)
                .put("TASK_PROGRESS_STEP", 10);

        AppConfig config = new AppConfig(raw);

        assertEquals(9090, config.httpPort());
        assertEquals("/v1/tasks", config.apiTasksPath());
        assertEquals("/ws/", config.wsPathPrefix());
        assertEquals("db.internal", config.dbHost());
        assertEquals(5433, config.dbPort());
        assertEquals("custom_db", config.dbName());
        assertEquals("app", config.dbUser());
        assertEquals("secret", config.dbPassword());
        assertEquals(12, config.dbPoolMaxSize());
        assertEquals("custom.start", config.taskStartAddress());
        assertEquals("custom.progress", config.taskProgressAddress());
        assertEquals(50L, config.tickIntervalMs());
        assertEquals(10, config.progressStep());
    }

    @Test
    void fallsBackWhenIntegerValueIsInvalid() {
        AppConfig config = new AppConfig(new JsonObject().put("HTTP_PORT", "not-a-number"));

        assertEquals(8080, config.httpPort());
    }

    @Test
    void requiresDbPassword() {
        AppConfig config = new AppConfig(new JsonObject());

        IllegalStateException error = assertThrows(IllegalStateException.class, config::dbPassword);
        assertEquals(
                "DB_PASSWORD is not set. Provide it via an environment variable or conf/config.json",
                error.getMessage());
    }
}
