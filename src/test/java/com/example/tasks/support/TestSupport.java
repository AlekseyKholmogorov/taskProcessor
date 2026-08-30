package com.example.tasks.support;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Общие хелперы для тестов Vert.x.
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * Подбирает свободный TCP-порт.
     *
     * @return свободный порт
     */
    public static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate free port", e);
        }
    }

    /**
     * Базовая конфигурация HTTP/EventBus без параметров БД.
     *
     * @param httpPort порт HTTP-сервера
     * @return JSON конфигурации
     */
    public static JsonObject httpConfig(int httpPort) {
        return new JsonObject()
                .put("HTTP_PORT", httpPort)
                .put("HTTP_API_TASKS_PATH", "/api/tasks")
                .put("WS_PATH_PREFIX", "/ws/tasks/")
                .put("EVENTBUS_TASK_START", "task.start")
                .put("EVENTBUS_TASK_PROGRESS", "task.progress")
                .put("TASK_TICK_INTERVAL_MS", 40)
                .put("TASK_PROGRESS_STEP", 50);
    }

    /**
     * Конфигурация с параметрами PostgreSQL.
     *
     * @param httpPort порт HTTP-сервера
     * @param host     хост БД
     * @param port     порт БД
     * @param database имя БД
     * @param user     пользователь
     * @param password пароль
     * @return JSON конфигурации
     */
    public static JsonObject fullConfig(
            int httpPort,
            String host,
            int port,
            String database,
            String user,
            String password) {
        return httpConfig(httpPort)
                .put("DB_HOST", host)
                .put("DB_PORT", port)
                .put("DB_NAME", database)
                .put("DB_USER", user)
                .put("DB_PASSWORD", password)
                .put("DB_POOL_MAX_SIZE", 4);
    }
}
