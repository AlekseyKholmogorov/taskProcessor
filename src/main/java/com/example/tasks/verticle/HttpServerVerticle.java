package com.example.tasks.verticle;

import com.example.tasks.config.AppConfig;
import com.example.tasks.model.TaskProgress;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.web.TaskRoutes;
import com.example.tasks.web.WebSocketRegistry;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Вертикл HTTP-слоя.
 *
 * <p>Поднимает HTTP-сервер, монтирует REST-маршруты и обслуживает
 * WebSocket-подключения, пересылая в них уведомления о прогрессе,
 * которые приходят из EventBus.
 */
public class HttpServerVerticle extends VerticleBase {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticle.class);

    private final TaskRepository taskRepository;
    private final WebSocketRegistry socketRegistry = new WebSocketRegistry();

    private AppConfig appConfig;

    /**
     * Создаёт вертикл HTTP-слоя.
     *
     * @param taskRepository репозиторий задач, нужный REST-маршрутам
     */
    public HttpServerVerticle(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Future<?> start() {
        appConfig = new AppConfig(config());

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        new TaskRoutes(taskRepository, vertx.eventBus(), appConfig).mount(router);

        // Слушаем обновления прогресса из EventBus (от воркера)
        vertx.eventBus().<JsonObject>consumer(appConfig.taskProgressAddress(), message -> {
            TaskProgress progress = TaskProgress.fromJson(message.body());
            socketRegistry.send(progress.userId(), progress.toJson());
        });

        return vertx.createHttpServer()
                .requestHandler(router)
                .webSocketHandler(this::handleWebSocket)
                .listen(appConfig.httpPort())
                .onSuccess(server -> LOG.info("HTTP server started on port {}", server.actualPort()));
    }

    /**
     * Обработчик WebSocket-соединений.
     * Ожидает подключения по пути вида {@code /ws/tasks/{userId}}.
     *
     * @param ws сокет-соединение
     */
    private void handleWebSocket(ServerWebSocket ws) {
        String path = ws.path();
        String prefix = appConfig.wsPathPrefix();

        if (!path.startsWith(prefix)) {
            ws.close();
            return;
        }

        try {
            Integer userId = Integer.parseInt(path.substring(prefix.length()));
            socketRegistry.register(userId, ws);
            ws.writeTextMessage(new JsonObject().put("message", "Connected").encode());
        } catch (NumberFormatException e) {
            ws.close();
        }
    }
}
