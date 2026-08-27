package com.example.tasks;

import com.example.tasks.config.AppConfig;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.verticle.HttpServerVerticle;
import com.example.tasks.verticle.TaskWorkerVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Загрузочный вертикл приложения.
 *
 * <p>Читает конфигурацию, создаёт пул соединений с БД и разворачивает
 * прикладные вертиклы в порядке, гарантирующем готовность обработчика
 * задач до того, как HTTP-сервер начнёт принимать запросы.
 */
public class MainVerticle extends AbstractVerticle {

    private Pool dbPool;
    private AppConfig appConfig;

    /**
     * Точка входа для приложения на Vert.x 5.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        ConfigRetriever.create(vertx).getConfig()
                .compose(conf -> vertx.deployVerticle(
                        new MainVerticle(),
                        new DeploymentOptions().setConfig(conf)))
                .onSuccess(id -> System.out.println("MainVerticle успешно развернут. ID: " + id))
                .onFailure(err -> {
                    System.err.println("Ошибка при развертывании MainVerticle:");
                    err.printStackTrace();
                    System.exit(1);
                });
    }

    @Override
    public void start(Promise<Void> startPromise) {
        appConfig = new AppConfig(config());
        initDatabasePool();

        TaskRepository taskRepository = new TaskRepository(dbPool);
        DeploymentOptions options = new DeploymentOptions().setConfig(config());

        // Воркер поднимается первым: его консьюмер task.start должен быть
        // зарегистрирован до того, как HTTP-сервер начнёт принимать запросы
        vertx.deployVerticle(new TaskWorkerVerticle(taskRepository), options)
                .compose(id -> vertx.deployVerticle(new HttpServerVerticle(taskRepository), options))
                .onSuccess(id -> startPromise.complete())
                .onFailure(startPromise::fail);
    }

    /**
     * Инициализирует пул подключений к PostgreSQL на основе разобранной конфигурации.
     */
    private void initDatabasePool() {
        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(appConfig.dbHost())
                .setPort(appConfig.dbPort())
                .setDatabase(appConfig.dbName())
                .setUser(appConfig.dbUser())
                .setPassword(appConfig.dbPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(appConfig.dbPoolMaxSize());

        dbPool = PgBuilder.pool()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .using(vertx)
                .build();
    }
}
