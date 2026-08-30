package com.example.tasks;

import com.example.tasks.support.SharedPostgresContainer;
import com.example.tasks.support.TestSupport;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сквозной сценарий: WebSocket + REST + воркер + PostgreSQL (Testcontainers).
 *
 * <p>{@link MainVerticle} читает конфиг через {@code ConfigRetriever}, поэтому
 * тест пишет временный JSON и указывает его через {@code vertx-config-path}.
 */
@ExtendWith(VertxExtension.class)
class BackgroundTaskFlowIT {

    private int httpPort;
    private Path configFile;
    private Pool cleanupPool;
    private WebSocketClient webSocketClient;
    private WebClient webClient;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) throws Exception {
        httpPort = TestSupport.freePort();
        webSocketClient = vertx.createWebSocketClient();
        webClient = WebClient.create(vertx);

        PostgreSQLContainer postgres = SharedPostgresContainer.get();
        JsonObject conf = TestSupport.fullConfig(
                httpPort,
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName(),
                postgres.getUsername(),
                postgres.getPassword());

        configFile = Files.createTempFile("task-processor-test-", ".json");
        Files.writeString(configFile, conf.encodePrettily());
        System.setProperty("vertx-config-path", configFile.toAbsolutePath().toString());

        PgConnectOptions db = new PgConnectOptions()
                .setHost(postgres.getHost())
                .setPort(postgres.getFirstMappedPort())
                .setDatabase(postgres.getDatabaseName())
                .setUser(postgres.getUsername())
                .setPassword(postgres.getPassword());

        cleanupPool = PgBuilder.pool()
                .with(new PoolOptions().setMaxSize(2))
                .connectingTo(db)
                .using(vertx)
                .build();

        cleanupPool.query("DELETE FROM tasks").execute()
                .compose(v -> vertx.deployVerticle(new MainVerticle(), new DeploymentOptions()))
                .onComplete(context.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(VertxTestContext context) throws Exception {
        System.clearProperty("vertx-config-path");
        if (configFile != null) {
            Files.deleteIfExists(configFile);
        }
        if (cleanupPool == null) {
            context.completeNow();
            return;
        }
        cleanupPool.query("DELETE FROM tasks").execute()
                .compose(v -> cleanupPool.close())
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 15, timeUnit = TimeUnit.SECONDS)
    void deliversProgressOnlyToTaskOwner(VertxTestContext context) {
        List<JsonObject> messages = new ArrayList<>();
        Checkpoint completed = context.checkpoint();

        WebSocketConnectOptions wsOptions = new WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(httpPort)
                .setURI("/ws/tasks/1");

        webSocketClient.connect(wsOptions).onComplete(context.succeeding(ws -> ws.textMessageHandler(text -> {
            JsonObject message = new JsonObject(text);
            if ("Connected".equals(message.getString("message"))) {
                webClient.post(httpPort, "localhost", "/api/tasks")
                        .sendJsonObject(new JsonObject().put("userId", 1))
                        .onComplete(context.succeeding(response -> context.verify(() -> {
                            assertEquals(200, response.statusCode());
                            assertEquals("IN_PROGRESS", response.bodyAsJsonObject().getString("status"));
                            assertNotNull(response.bodyAsJsonObject().getInteger("taskId"));
                        })));
                return;
            }

            messages.add(message);
            context.verify(() -> {
                assertEquals(1, message.getInteger("userId"));
                assertTrue(message.getInteger("progress") > 0);
            });

            if ("COMPLETED".equals(message.getString("status"))) {
                context.verify(() -> {
                    assertEquals(100, message.getInteger("progress"));
                    assertTrue(messages.size() >= 2);
                });
                completed.flag();
            }
        })));
    }
}
