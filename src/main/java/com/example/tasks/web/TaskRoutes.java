package com.example.tasks.web;

import com.example.tasks.config.AppConfig;
import com.example.tasks.repository.TaskRepository;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * REST-маршруты для работы с фоновыми задачами.
 *
 * <p>Класс знает, по какому пути и какой обработчик регистрировать,
 * но не управляет жизненным циклом HTTP-сервера — это забота вертикла.
 */
public class TaskRoutes {

    private final TaskRepository taskRepository;
    private final EventBus eventBus;
    private final AppConfig appConfig;

    /**
     * Создаёт набор маршрутов.
     *
     * @param taskRepository репозиторий задач
     * @param eventBus       шина для постановки задачи в обработку
     * @param appConfig      конфигурация приложения
     */
    public TaskRoutes(TaskRepository taskRepository, EventBus eventBus, AppConfig appConfig) {
        this.taskRepository = taskRepository;
        this.eventBus = eventBus;
        this.appConfig = appConfig;
    }

    /**
     * Регистрирует маршруты в переданном роутере.
     *
     * @param router роутер, в который монтируются маршруты
     */
    public void mount(Router router) {
        router.post(appConfig.apiTasksPath()).handler(this::handleStartTask);
    }

    /**
     * Обработчик запроса на запуск новой задачи.
     * Ожидает JSON вида {"userId": 1}.
     *
     * @param ctx контекст маршрутизации
     */
    private void handleStartTask(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null || !body.containsKey("userId")) {
            ctx.response().setStatusCode(400).end("Missing userId");
            return;
        }

        Integer userId = body.getInteger("userId");

        taskRepository.createTask(userId)
                .onSuccess(taskId -> {
                    // Отправляем задачу воркеру через EventBus
                    JsonObject taskData = new JsonObject().put("taskId", taskId).put("userId", userId);
                    eventBus.send(appConfig.taskStartAddress(), taskData);

                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject().put("taskId", taskId).put("status", "STARTED").encode());
                })
                .onFailure(err -> ctx.response().setStatusCode(500).end("DB Error: " + err.getMessage()));
    }
}
