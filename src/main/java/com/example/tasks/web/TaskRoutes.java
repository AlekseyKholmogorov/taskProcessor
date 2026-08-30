package com.example.tasks.web;

import com.example.tasks.config.AppConfig;
import com.example.tasks.model.TaskStatus;
import com.example.tasks.repository.TaskRepository;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST-маршруты для работы с фоновыми задачами.
 *
 * <p>Класс знает, по какому пути и какой обработчик регистрировать,
 * но не управляет жизненным циклом HTTP-сервера — это забота вертикла.
 */
public class TaskRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(TaskRoutes.class);

    private static final String USER_ID = "userId";
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_SERVER_ERROR = 500;
    private static  final int SEND_TIMEOUT = 5000;

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
        if (body == null || !body.containsKey(USER_ID)) {
            ctx.response().setStatusCode(STATUS_BAD_REQUEST).end("Missing userId");
            return;
        }

        Integer userId = body.getInteger(USER_ID);

        createAndDispatchTask(userId)
                .onSuccess(taskId -> ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("taskId", taskId).put("status", TaskStatus.IN_PROGRESS.name()).encode()))
                .onFailure(err -> {
                    LOG.error("Failed to start task for user {}", userId, err);
                    ctx.response().setStatusCode(STATUS_SERVER_ERROR).end("Task worker unavailable");
                });
    }

    private Future<Integer> createAndDispatchTask(Integer userId) {
        return taskRepository.createTask(userId)
                .compose(taskId -> {
                    JsonObject taskData = new JsonObject().put("taskId", taskId).put(USER_ID, userId);
                    DeliveryOptions delivery = new DeliveryOptions().setSendTimeout(SEND_TIMEOUT);

                    return eventBus.<JsonObject>request(appConfig.taskStartAddress(), taskData, delivery)
                            .map(reply -> taskId) // Если успех, пробрасываем taskId дальше
                            .recover(err -> {
                                // В случае ошибки EventBus запускаем компенсацию
                                LOG.warn("Worker didn't respond for task {}. Rolling back...", taskId);
                                return taskRepository.deleteTask(taskId)
                                        .compose(v -> Future.failedFuture(err));
                            });
                });
    }
}
