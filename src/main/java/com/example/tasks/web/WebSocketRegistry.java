package com.example.tasks.web;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр активных WebSocket-соединений, сгруппированных по идентификатору пользователя.
 *
 * <p>Обеспечивает адресную доставку: сообщение уходит только в сокет того
 * пользователя, которому оно предназначено.
 */
public class WebSocketRegistry {

    private final Map<Integer, ServerWebSocket> activeSockets = new ConcurrentHashMap<>();

    /**
     * Регистрирует соединение и вешает обработчик его закрытия.
     *
     * @param userId идентификатор пользователя
     * @param ws     установленное соединение
     */
    public void register(Integer userId, ServerWebSocket ws) {
        activeSockets.put(userId, ws);
        ws.closeHandler(v -> unregister(userId));
    }

    /**
     * Удаляет соединение пользователя из реестра.
     *
     * @param userId идентификатор пользователя
     */
    public void unregister(Integer userId) {
        activeSockets.remove(userId);
    }

    /**
     * Отправляет сообщение в соединение пользователя, если оно активно.
     *
     * @param userId  идентификатор пользователя
     * @param message сообщение для отправки
     */
    public void send(Integer userId, JsonObject message) {
        ServerWebSocket ws = activeSockets.get(userId);
        if (ws != null && !ws.isClosed()) {
            ws.writeTextMessage(message.encode());
        }
    }
}
