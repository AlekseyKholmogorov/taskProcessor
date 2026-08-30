package com.example.tasks.verticle;

import com.example.tasks.repository.TaskRepository;
import com.example.tasks.support.TestSupport;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class HttpServerVerticleTest {

    @Mock
    private TaskRepository taskRepository;

    private int port;
    private WebSocketClient webSocketClient;
    private WebClient webClient;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) {
        port = TestSupport.freePort();
        webSocketClient = vertx.createWebSocketClient();
        webClient = WebClient.create(vertx);
        DeploymentOptions options = new DeploymentOptions().setConfig(TestSupport.httpConfig(port));
        vertx.deployVerticle(new HttpServerVerticle(taskRepository), options)
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void websocketReceivesOnlyOwnProgress(Vertx vertx, VertxTestContext context) {
        CountDownLatch connected = context.checkpoint().asLatch(2);
        Checkpoint progress = context.checkpoint();

        WebSocketConnectOptions userOne = new WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(port)
                .setURI("/ws/tasks/1");
        WebSocketConnectOptions userTwo = new WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(port)
                .setURI("/ws/tasks/2");

        webSocketClient.connect(userOne).onComplete(context.succeeding(ws -> ws.textMessageHandler(text -> {
            JsonObject message = new JsonObject(text);
            if ("Connected".equals(message.getString("message"))) {
                connected.countDown();
                return;
            }
            context.verify(() -> {
                assertEquals(1, message.getInteger("userId"));
                assertEquals(40, message.getInteger("progress"));
            });
            progress.flag();
        })));

        webSocketClient.connect(userTwo).onComplete(context.succeeding(ws -> ws.textMessageHandler(text -> {
            JsonObject message = new JsonObject(text);
            if ("Connected".equals(message.getString("message"))) {
                connected.countDown();
                vertx.eventBus().publish("task.progress", new JsonObject()
                        .put("taskId", 10)
                        .put("userId", 2)
                        .put("progress", 20)
                        .put("status", "IN_PROGRESS"));
                vertx.eventBus().publish("task.progress", new JsonObject()
                        .put("taskId", 11)
                        .put("userId", 1)
                        .put("progress", 40)
                        .put("status", "IN_PROGRESS"));
            }
        })));
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void closesWebsocketForInvalidPath(VertxTestContext context) {
        WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(port)
                .setURI("/ws/tasks/not-a-number");

        webSocketClient.connect(options).onComplete(ar -> {
            if (ar.succeeded()) {
                ar.result().closeHandler(v -> context.completeNow());
            } else {
                context.completeNow();
            }
        });
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void postCreatesTaskThroughRoutes(Vertx vertx, VertxTestContext context) {
        when(taskRepository.createTask(5)).thenReturn(Future.succeededFuture(100));
        vertx.eventBus().<JsonObject>consumer("task.start", message ->
                message.reply(new JsonObject().put("accepted", true)));

        webClient.post(port, "localhost", "/api/tasks")
                .sendJsonObject(new JsonObject().put("userId", 5))
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    assertEquals(100, response.bodyAsJsonObject().getInteger("taskId"));
                    assertTrue(response.bodyAsJsonObject().containsKey("status"));
                    context.completeNow();
                })));
    }
}
