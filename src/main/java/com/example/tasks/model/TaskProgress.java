package com.example.tasks.model;

import io.vertx.core.json.JsonObject;

/**
 * Уведомление о прогрессе выполнения задачи.
 *
 * <p>Это формат сообщения, которое воркер публикует в EventBus,
 * а сервер пересылает в WebSocket владельца задачи.
 *
 * @param taskId   идентификатор задачи
 * @param userId   идентификатор пользователя-владельца
 * @param progress текущий прогресс в процентах, от 0 до 100
 * @param status   статус задачи, соответствующий значению прогресса
 */
public record TaskProgress(int taskId, int userId, int progress, TaskStatus status) {

    public static final int COMPLETE_PROGRESS = 100;

    /**
     * Создаёт уведомление, выводя статус из значения прогресса.
     *
     * @param taskId   идентификатор задачи
     * @param userId   идентификатор пользователя-владельца
     * @param progress текущий прогресс в процентах
     * @return уведомление с согласованными прогрессом и статусом
     */
    public static TaskProgress of(int taskId, int userId, int progress) {
        TaskStatus status = progress >= COMPLETE_PROGRESS ? TaskStatus.COMPLETED : TaskStatus.IN_PROGRESS;
        return new TaskProgress(taskId, userId, progress, status);
    }

    /**
     * Восстанавливает уведомление из JSON.
     *
     * @param json объект, полученный из EventBus
     * @return разобранное уведомление
     */
    public static TaskProgress fromJson(JsonObject json) {
        return new TaskProgress(
                json.getInteger("taskId"),
                json.getInteger("userId"),
                json.getInteger("progress"),
                TaskStatus.valueOf(json.getString("status")));
    }

    /**
     * Сериализует уведомление в формат, ожидаемый клиентом.
     *
     * @return JSON-представление
     */
    public JsonObject toJson() {
        return new JsonObject()
                .put("taskId", taskId)
                .put("userId", userId)
                .put("progress", progress)
                .put("status", status.name());
    }
}
