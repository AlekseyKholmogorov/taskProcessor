package com.example.tasks.verticle;

import com.example.tasks.config.AppConfig;
import com.example.tasks.model.TaskStatus;
import com.example.tasks.repository.TaskRepository;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Вертикл для имитации фоновой обработки длительных задач.
 * Не блокирует EventLoop основного потока благодаря использованию таймеров и асинхронного EventBus.
 */
public class TaskWorkerVerticle extends VerticleBase {

    private static final Logger LOG = LoggerFactory.getLogger(TaskWorkerVerticle.class);

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
            processTask(taskData.getInteger("taskId"));
        }).completion();
    }

    /**
     * Асинхронно обрабатывает задачу, имитируя длительный процесс с помощью таймера.
     *
     * @param taskId идентификатор задачи
     */
    private void processTask(Integer taskId) {
        // Устанавливаем таймер, который срабатывает каждые 1000мс (1 сек)
        vertx.setPeriodic(appConfig.tickIntervalMs(), id -> onTick(taskId, id));
    }

    /**
     * Обрабатывает очередной тик: инкрементирует прогресс и рассылает
     * обновление, либо останавливает таймер в терминальных состояниях.
     *
     * @param taskId  идентификатор задачи
     * @param timerId идентификатор таймера для отмены при завершении
     */
    private void onTick(Integer taskId, Long timerId) {
        taskRepository.incrementProgress(taskId, appConfig.progressStep())
                .onSuccess(progress -> {
                    if (progress == null) {
                        LOG.warn("Task {} no longer exists, cancelling timer", taskId);
                        vertx.cancelTimer(timerId);
                        return;
                    }

                    if (progress.status() == TaskStatus.COMPLETED) {
                        vertx.cancelTimer(timerId);
                    }

                    vertx.eventBus().publish(appConfig.taskProgressAddress(), progress.toJson());
                })
                .onFailure(err -> {
                    LOG.error("Failed to increment task {}, cancelling timer", taskId, err);
                    vertx.cancelTimer(timerId);
                });
    }
}
