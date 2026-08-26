package com.example.tasks;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

/**
 * Вертикл для имитации фоновой обработки длительных задач.
 * Не блокирует EventLoop основного потока благодаря использованию таймеров и асинхронного EventBus.
 */
public class WorkerVerticle extends AbstractVerticle {

    private final Pool dbPool;

    /**
     * Конструктор WorkerVerticle.
     *
     * @param dbPool пул соединений с базой данных для обновления статуса задачи
     */
    public WorkerVerticle(Pool dbPool) {
        this.dbPool = dbPool;
    }

    @Override
    public void start() {
        vertx.eventBus().<JsonObject>consumer("task.start", message -> {
            JsonObject taskData = message.body();
            processTask(taskData.getInteger("taskId"), taskData.getInteger("userId"));
        });
    }

    /**
     * Асинхронно обрабатывает задачу, имитируя длительный процесс с помощью таймера.
     *
     * @param taskId идентификатор задачи
     * @param userId идентификатор пользователя-владельца
     */
    private void processTask(Integer taskId, Integer userId) {
        // Устанавливаем таймер, который срабатывает каждые 1000мс (1 сек)
        vertx.setPeriodic(1000, id -> {
            updateProgress(taskId, userId, id);
        });
    }

    /**
     * Инкрементирует прогресс выполнения задачи, обновляет БД и рассылает уведомления.
     *
     * @param taskId идентификатор задачи
     * @param userId идентификатор пользователя
     * @param timerId идентификатор таймера для его отмены при завершении
     */
    private void updateProgress(Integer taskId, Integer userId, Long timerId) {
        dbPool.preparedQuery("SELECT progress FROM tasks WHERE id = $1")
                .execute(Tuple.of(taskId))
                .onSuccess(rowSet -> {
                    if (rowSet.iterator().hasNext()) {
                        int currentProgress = rowSet.iterator().next().getInteger("progress");
                        int newProgress = currentProgress + 20;

                        if (newProgress >= 100) {
                            newProgress = 100;
                            vertx.cancelTimer(timerId);
                            updateTaskInDb(taskId, "COMPLETED", newProgress);
                        } else {
                            updateTaskInDb(taskId, "IN_PROGRESS", newProgress);
                        }

                        JsonObject update = new JsonObject()
                                .put("taskId", taskId)
                                .put("userId", userId)
                                .put("progress", newProgress)
                                .put("status", newProgress == 100 ? "COMPLETED" : "IN_PROGRESS");

                        vertx.eventBus().publish("task.progress", update);
                    }
                })
                .onFailure(err -> System.err.println("Failed to fetch progress: " + err.getMessage()));
    }

    /**
     * Обновляет статус и прогресс задачи в базе данных.
     *
     * @param taskId идентификатор задачи
     * @param status новый статус задачи
     * @param progress текущий прогресс (0-100)
     */
    private void updateTaskInDb(Integer taskId, String status, int progress) {
        dbPool.preparedQuery("UPDATE tasks SET status = $1, progress = $2 WHERE id = $3")
                .execute(Tuple.of(status, progress, taskId))
                .onFailure(err -> System.err.println("Failed to update task: " + err.getMessage()));
    }
}