package com.example.tasks;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Главный вертикл приложения.
 * Отвечает за инициализацию HTTP-сервера, маршрутизацию REST API,
 * управление WebSocket-соединениями и подключение к БД PostgreSQL.
 */
public class MainVerticle extends AbstractVerticle {

    private Pool dbPool;
    private final Map<Integer, ServerWebSocket> activeSockets = new ConcurrentHashMap<>();

    /**
     * Точка входа для приложения на Vert.x 5.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle())
                .onSuccess(id -> System.out.println("MainVerticle успешно развернут. ID: " + id))
                .onFailure(err -> {
                    System.err.println("Ошибка при развертывании MainVerticle:");
                    err.printStackTrace();
                    System.exit(1);
                });
    }

    @Override
    public void start(Promise<Void> startPromise) {
        initDatabasePool();

        // Деплой воркера для фоновых задач
        vertx.deployVerticle(new WorkerVerticle(dbPool));

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // REST API
        router.post("/api/tasks").handler(this::handleStartTask);

        // Слушаем обновления прогресса из EventBus (от воркера)
        vertx.eventBus().<JsonObject>consumer("task.progress", message -> {
            JsonObject update = message.body();
            Integer userId = update.getInteger("userId");
            ServerWebSocket ws = activeSockets.get(userId);
            if (ws != null && !ws.isClosed()) {
                ws.writeTextMessage(update.encode());
            }
        });

        // Запуск HTTP-сервера
        vertx.createHttpServer()
                .requestHandler(router)
                .webSocketHandler(this::handleWebSocket)
                .listen(8080)
                .onSuccess(server -> {
                    System.out.println("HTTP сервер запущен на порту 8080");
                    startPromise.complete();
                })
                .onFailure(err -> {
                    System.err.println("Ошибка запуска HTTP-сервера: " + err.getMessage());
                    startPromise.fail(err);
                });
    }

    /**
     * Инициализирует пул подключений к PostgreSQL с использованием конфигурации
     * из переменных окружения.
     */
    private void initDatabasePool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setPort(Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432")))
                .setHost(System.getenv().getOrDefault("DB_HOST", "localhost"))
                .setDatabase(System.getenv().getOrDefault("DB_NAME", "task_db"))
                .setUser(System.getenv().getOrDefault("DB_USER", "vertx"))
                .setPassword(System.getenv().getOrDefault("DB_PASSWORD", "vertx_password"));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        dbPool = PgBuilder.pool()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
    }

    /**
     * Обработчик REST-запроса на запуск новой задачи.
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

        dbPool.preparedQuery("INSERT INTO tasks (user_id, status, progress) VALUES ($1, 'IN_PROGRESS', 0) RETURNING id")
                .execute(Tuple.of(userId))
                .onSuccess(rowSet -> {
                    // Успешное выполнение запроса
                    Row row = rowSet.iterator().next();
                    Integer taskId = row.getInteger("id");

                    // Отправляем задачу воркеру через EventBus
                    JsonObject taskData = new JsonObject().put("taskId", taskId).put("userId", userId);
                    vertx.eventBus().send("task.start", taskData);

                    ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(new JsonObject().put("taskId", taskId).put("status", "STARTED").encode());
                })
                .onFailure(err -> {
                    // Ошибка выполнения запроса
                    ctx.response().setStatusCode(500).end("DB Error: " + err.getMessage());
                });
    }

    /**
     * Обработчик WebSocket-соединений.
     * Ожидает подключения по пути /ws/tasks/{userId}.
     *
     * @param ws сокет-соединение
     */
    private void handleWebSocket(ServerWebSocket ws) {
        String path = ws.path();
        if (path.startsWith("/ws/tasks/")) {
            try {
                String userIdStr = path.substring("/ws/tasks/".length());
                Integer userId = Integer.parseInt(userIdStr);

                activeSockets.put(userId, ws);
                ws.closeHandler(v -> activeSockets.remove(userId));

                ws.writeTextMessage(new JsonObject().put("message", "Connected").encode());
            } catch (NumberFormatException e) {
                ws.close();
            }
        } else {
            ws.close();
        }
    }
}