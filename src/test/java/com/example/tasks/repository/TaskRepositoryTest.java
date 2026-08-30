package com.example.tasks.repository;

import com.example.tasks.model.TaskStatus;
import com.example.tasks.support.SharedPostgresContainer;
import io.vertx.core.Vertx;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(VertxExtension.class)
class TaskRepositoryTest {

    private Pool pool;
    private TaskRepository repository;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext context) {
        PostgreSQLContainer postgres = SharedPostgresContainer.get();
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(postgres.getHost())
                .setPort(postgres.getFirstMappedPort())
                .setDatabase(postgres.getDatabaseName())
                .setUser(postgres.getUsername())
                .setPassword(postgres.getPassword());

        pool = PgBuilder.pool()
                .with(new PoolOptions().setMaxSize(4))
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
        repository = new TaskRepository(pool);

        pool.query("DELETE FROM tasks").execute()
                .onComplete(context.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(VertxTestContext context) {
        if (pool == null) {
            context.completeNow();
            return;
        }
        pool.query("DELETE FROM tasks").execute()
                .compose(v -> pool.close())
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    void createAndIncrementUntilCompleted(VertxTestContext context) {
        repository.createTask(1)
                .compose(taskId -> repository.incrementProgress(taskId, 60)
                        .compose(first -> {
                            context.verify(() -> {
                                assertEquals(1, first.userId());
                                assertEquals(60, first.progress());
                                assertEquals(TaskStatus.IN_PROGRESS, first.status());
                            });
                            return repository.incrementProgress(taskId, 60);
                        })
                        .map(second -> {
                            context.verify(() -> {
                                assertEquals(100, second.progress());
                                assertEquals(TaskStatus.COMPLETED, second.status());
                            });
                            return taskId;
                        }))
                .onComplete(context.succeedingThenComplete());
    }

    @Test
    void incrementReturnsNullForMissingTask(VertxTestContext context) {
        repository.incrementProgress(999_999, 20)
                .onComplete(context.succeeding(progress -> context.verify(() -> {
                    assertNull(progress);
                    context.completeNow();
                })));
    }

    @Test
    void deleteRemovesTask(VertxTestContext context) {
        repository.createTask(1)
                .compose(taskId -> repository.deleteTask(taskId)
                        .compose(v -> repository.incrementProgress(taskId, 20)))
                .onComplete(context.succeeding(progress -> context.verify(() -> {
                    assertNull(progress);
                    context.completeNow();
                })));
    }
}
