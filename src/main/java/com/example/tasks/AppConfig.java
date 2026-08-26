package com.example.tasks;

import io.vertx.core.json.JsonObject;

/**
 * Типобезопасный доступ к конфигурации приложения.
 *
 * <p>Значения приходят из стандартной цепочки источников Vert.x Config
 * (в порядке возрастания приоритета): системные свойства, переменные окружения,
 * файл {@code conf/config.json}. Имена ключей во всех источниках совпадают
 * и записываются в верхнем регистре через подчёркивание, например {@code DB_HOST}.
 *
 * <p>Если ключ не задан ни в одном источнике, используется значение по умолчанию,
 * зашитое в соответствующем методе этого класса.
 */
public final class AppConfig {

    private final JsonObject raw;

    /**
     * Создаёт обёртку над конфигурацией.
     *
     * @param raw конфигурация из ConfigRetriever; допускается {@code null}
     */
    public AppConfig(JsonObject raw) {
        this.raw = raw == null ? new JsonObject() : raw;
    }

    /**
     * Возвращает порт HTTP-сервера.
     *
     * @return номер TCP-порта
     */
    public int httpPort() {
        return integer("HTTP_PORT", 8080);
    }

    /**
     * Возвращает путь REST-эндпоинта создания задачи.
     *
     * @return путь, начинающийся со слеша
     */
    public String apiTasksPath() {
        return string("HTTP_API_TASKS_PATH", "/api/tasks");
    }

    /**
     * Возвращает префикс пути WebSocket-подключения.
     *
     * @return префикс, завершающийся слешем
     */
    public String wsPathPrefix() {
        return string("WS_PATH_PREFIX", "/ws/tasks/");
    }

    /**
     * Возвращает хост PostgreSQL.
     *
     * @return имя хоста или IP-адрес
     */
    public String dbHost() {
        return string("DB_HOST", "localhost");
    }

    /**
     * Возвращает порт PostgreSQL.
     *
     * @return номер TCP-порта
     */
    public int dbPort() {
        return integer("DB_PORT", 5432);
    }

    /**
     * Возвращает имя базы данных.
     *
     * @return имя БД
     */
    public String dbName() {
        return string("DB_NAME", "task_db");
    }

    /**
     * Возвращает имя пользователя БД.
     *
     * @return логин
     */
    public String dbUser() {
        return string("DB_USER", "vertx");
    }

    /**
     * Возвращает пароль пользователя БД.
     *
     * <p>Значения по умолчанию нет сознательно: незаданный пароль — это ошибка
     * конфигурации, а не повод молча подключаться с угаданным значением.
     *
     * @return пароль
     * @throws IllegalStateException если ключ {@code DB_PASSWORD} не задан ни в одном источнике
     */
    public String dbPassword() {
        return required("DB_PASSWORD");
    }

    /**
     * Возвращает максимальный размер пула соединений с БД.
     *
     * @return число соединений
     */
    public int dbPoolMaxSize() {
        return integer("DB_POOL_MAX_SIZE", 5);
    }

    /**
     * Возвращает адрес EventBus для постановки задачи в обработку.
     *
     * @return адрес шины
     */
    public String taskStartAddress() {
        return string("EVENTBUS_TASK_START", "task.start");
    }

    /**
     * Возвращает адрес EventBus для рассылки прогресса.
     *
     * @return адрес шины
     */
    public String taskProgressAddress() {
        return string("EVENTBUS_TASK_PROGRESS", "task.progress");
    }

    /**
     * Возвращает период тика имитации обработки.
     *
     * @return интервал в миллисекундах
     */
    public long tickIntervalMs() {
        return integer("TASK_TICK_INTERVAL_MS", 1000);
    }

    /**
     * Возвращает шаг приращения прогресса за один тик.
     *
     * @return величина шага в процентах
     */
    public int progressStep() {
        return integer("TASK_PROGRESS_STEP", 20);
    }

    /**
     * Читает строковое значение с подстановкой умолчания.
     *
     * @param key      имя ключа
     * @param fallback значение по умолчанию
     * @return найденное значение, приведённое к строке
     */
    private String string(String key, String fallback) {
        Object value = raw.getValue(key);
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    /**
     * Читает целочисленное значение с подстановкой умолчания.
     *
     * <p>Устойчиво к тому, что источник мог вернуть значение как числом,
     * так и строкой: переменные окружения приходят строками, JSON — числами.
     *
     * @param key      имя ключа
     * @param fallback значение по умолчанию
     * @return разобранное число либо {@code fallback}, если разбор не удался
     */
    private int integer(String key, int fallback) {
        Object value = raw.getValue(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Читает обязательное строковое значение.
     *
     * @param key имя ключа
     * @return значение, приведённое к строке
     * @throws IllegalStateException если ключ не задан
     */
    private String required(String key) {
        Object value = raw.getValue(key);
        if (value == null) {
            throw new IllegalStateException(
                    key + " is not set. Provide it via an environment variable or conf/config.json");
        }
        return String.valueOf(value);
    }
}
