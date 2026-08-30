package com.example.tasks.web;

import com.example.tasks.config.AppConfig;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.support.TestSupport;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class TaskRoutesTest {

    @Mock
    private TaskRepository taskRepository;

    private int port;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) {
        port = TestSupport.freePort();
        JsonObject config = TestSupport.httpConfig(port);
        AppConfig appConfig = new AppConfig(config);

        vertx.deployVerticle(new VerticleBase() {
            @Override
            public Future<?> start() {
                Router router = Router.router(vertx);
                router.route().handler(BodyHandler.create());
                new TaskRoutes(taskRepository, vertx.eventBus(), appConfig).mount(router);
                return vertx.createHttpServer()
                        .requestHandler(router)
                        .listen(port);
            }
        }).onComplete(context.succeedingThenComplete());
    }

    @Test
    void rejectsMissingUserId(Vertx vertx, VertxTestContext context) {
        WebClient.create(vertx)
                .post(port, "localhost", "/api/tasks")
                .sendJsonObject(new JsonObject().put("other", 1))
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(400, response.statusCode());
                    assertEquals("Missing userId", response.bodyAsString());
                    context.completeNow();
                })));
    }

    @Test
    void startsTaskWhenWorkerAccepts(Vertx vertx, VertxTestContext context) {
        when(taskRepository.createTask(7)).thenReturn(Future.succeededFuture(42));

        vertx.eventBus().<JsonObject>consumer("task.start", message ->
                message.reply(new JsonObject().put("accepted", true)));

        WebClient.create(vertx)
                .post(port, "localhost", "/api/tasks")
                .sendJsonObject(new JsonObject().put("userId", 7))
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(200, response.statusCode());
                    JsonObject body = response.bodyAsJsonObject();
                    assertEquals(42, body.getInteger("taskId"));
                    assertEquals("IN_PROGRESS", body.getString("status"));
                    verify(taskRepository, never()).deleteTask(anyInt());
                    context.completeNow();
                })));
    }

    @Test
    void rollsBackWhenWorkerUnavailable(Vertx vertx, VertxTestContext context) {
        when(taskRepository.createTask(3)).thenReturn(Future.succeededFuture(15));
        when(taskRepository.deleteTask(15)).thenReturn(Future.succeededFuture());
        vertx.eventBus().<JsonObject>consumer("task.start", message -> message.fail(503, "down"));

        WebClient.create(vertx)
                .post(port, "localhost", "/api/tasks")
                .sendJsonObject(new JsonObject().put("userId", 3))
                .onComplete(context.succeeding(response -> context.verify(() -> {
                    assertEquals(500, response.statusCode());
                    assertTrue(response.bodyAsString().contains("Task worker unavailable"));
                    verify(taskRepository).deleteTask(eq(15));
                    context.completeNow();
                })));
    }
}
