package com.example.tasks.verticle;

import com.example.tasks.config.AppConfig;
import com.example.tasks.model.TaskProgress;
import com.example.tasks.model.TaskStatus;
import com.example.tasks.repository.TaskRepository;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;

/**
 * Вертикл для имитации фоновой обработки длительных задач.
 * Не блокирует EventLoop основного потока благодаря использованию таймеров и асинхронного EventBus.
 */
public class TaskWorkerVerticle extends VerticleBase {

    private final TaskRepository taskRepository;
    private AppConfig appConfig;

    /**
     * Конструктор вертикла-обработчика.
     *
     * @param taskRepository репозиторий для чтения и обновления состояния задач
     */
    public TaskWorkerVerticle(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Future<?> start() {
        appConfig = new AppConfig(config());
        return vertx.eventBus().<JsonObject>consumer(appConfig.taskStartAddress(), message -> {
            JsonObject taskData = message.body();
            processTask(taskData.getInteger("taskId"), taskData.getInteger("userId"));
        }).completion();
    }

    /**
     * Асинхронно обрабатывает задачу, имитируя длительный процесс с помощью таймера.
     *
     * @param taskId идентификатор задачи
     * @param userId идентификатор пользователя-владельца
     */
    private void processTask(Integer taskId, Integer userId) {
        // Устанавливаем таймер, который срабатывает каждые 1000мс (1 сек)
        vertx.setPeriodic(appConfig.tickIntervalMs(), id -> {
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
        taskRepository.findProgress(taskId)
                .onSuccess(currentProgress -> {
                    if (currentProgress == null) {
                        return;
                    }
                    int newProgress = Math.min(currentProgress + appConfig.progressStep(), 100);

                    if (newProgress >= 100) {
                        vertx.cancelTimer(timerId);
                    }

                    TaskProgress progress = TaskProgress.of(taskId, userId, newProgress);
                    updateTaskInDb(taskId, progress.status(), newProgress);
                    vertx.eventBus().publish(appConfig.taskProgressAddress(), progress.toJson());
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
    private void updateTaskInDb(Integer taskId, TaskStatus status, int progress) {
        taskRepository.updateProgress(taskId, status, progress)
                .onFailure(err -> System.err.println("Failed to update task: " + err.getMessage()));
    }
}
