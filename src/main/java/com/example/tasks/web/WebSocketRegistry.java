package com.example.tasks.web;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр активных WebSocket-соединений, сгруппированных по идентификатору пользователя.
 *
 * <p>Обеспечивает адресную доставку: сообщение уходит только в сокет того
 * пользователя, которому оно предназначено.
 */
public class WebSocketRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketRegistry.class);

    private final Map<Integer, ServerWebSocket> activeSockets = new ConcurrentHashMap<>();

    /**
     * Регистрирует соединение и вешает обработчик его закрытия.
     *
     * @param userId идентификатор пользователя
     * @param ws     установленное соединение
     */
    public void register(Integer userId, ServerWebSocket ws) {
        activeSockets.put(userId, ws);
        ws.closeHandler(v -> {
            if (activeSockets.remove(userId, ws)) {
                LOG.info(
                        "User disconnected. Removed WebSocket for userId={}. Close status: {}",
                        userId,
                        ws.closeStatusCode()
                );
            } else {
                LOG.debug("Stale WebSocket closed for userId={}. Active connection preserved.", userId);
            }
        });
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
