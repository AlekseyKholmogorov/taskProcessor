package com.example.tasks;

import com.example.tasks.config.AppConfig;
import com.example.tasks.repository.TaskRepository;
import com.example.tasks.verticle.HttpServerVerticle;
import com.example.tasks.verticle.TaskWorkerVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
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
public class MainVerticle extends VerticleBase {

    private Pool dbPool;
    private AppConfig appConfig;

    @Override
    public Future<?> start() {
        return ConfigRetriever.create(vertx).getConfig()
                .compose(this::bootstrap);
    }

    @Override
    public Future<?> stop() {
        return dbPool == null ? Future.succeededFuture() : dbPool.close();
    }

    /**
     * Собирает граф объектов и разворачивает прикладные вертиклы.
     *
     * @param conf разобранная конфигурация приложения
     * @return результат разворачивания
     */
    private Future<?> bootstrap(JsonObject conf) {
        appConfig = new AppConfig(conf);
        initDatabasePool();

        TaskRepository taskRepository = new TaskRepository(dbPool);
        DeploymentOptions options = new DeploymentOptions().setConfig(conf);

        // Воркер поднимается первым: его консьюмер task.start должен быть
        // зарегистрирован до того, как HTTP-сервер начнёт принимать запросы
        return vertx.deployVerticle(new TaskWorkerVerticle(taskRepository), options)
                .compose(id -> vertx.deployVerticle(new HttpServerVerticle(taskRepository), options));
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
