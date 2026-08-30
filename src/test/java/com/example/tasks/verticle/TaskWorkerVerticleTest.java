package com.example.tasks.verticle;

import com.example.tasks.model.TaskProgress;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.support.TestSupport;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class TaskWorkerVerticleTest {

    @Mock
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) {
        DeploymentOptions options = new DeploymentOptions().setConfig(TestSupport.httpConfig(0));
        vertx.deployVerticle(new TaskWorkerVerticle(taskRepository), options)
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    void failsWhenTaskIdMissing(Vertx vertx, VertxTestContext context) {
        vertx.eventBus().<JsonObject>request("task.start", new JsonObject())
                .onComplete(context.failing(err -> context.verify(() -> {
                    assertTrue(err.getMessage().contains("taskId is required"));
                    context.completeNow();
                })));
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void publishesProgressUntilCompleted(Vertx vertx, VertxTestContext context) {
        AtomicInteger calls = new AtomicInteger();
        when(taskRepository.incrementProgress(eq(9), eq(50))).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return Future.succeededFuture(TaskProgress.of(9, 1, 50));
            }
            return Future.succeededFuture(TaskProgress.of(9, 1, 100));
        });

        CountDownLatch progressCheckpoint = context.checkpoint().asLatch(2);

        vertx.eventBus().<JsonObject>consumer("task.progress", message -> {
            JsonObject body = message.body();
            context.verify(() -> {
                assertEquals(9, body.getInteger("taskId"));
                assertEquals(1, body.getInteger("userId"));
                assertTrue(body.getInteger("progress") == 50 || body.getInteger("progress") == 100);
            });
            progressCheckpoint.countDown();
        });

        vertx.eventBus().request("task.start", new JsonObject().put("taskId", 9).put("userId", 1))
                .onComplete(context.succeeding(reply -> context.verify(() ->
                        assertTrue(((JsonObject) reply.body()).getBoolean("accepted")))));
    }
}
