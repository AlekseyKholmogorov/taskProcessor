package com.example.tasks.repository;

import com.example.tasks.model.TaskStatus;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.Tuple;

/**
 * Доступ к таблице {@code tasks}.
 *
 * <p>Скрывает детали работы с SQL-клиентом: наружу отдаются только доменные
 * значения, обёрнутые в {@link Future}. Классы {@code Row}, {@code RowSet}
 * и {@code Tuple} за пределы этого класса не выходят.
 */
public class TaskRepository {

    private static final String SQL_INSERT_TASK =
            "INSERT INTO tasks (user_id, status, progress) VALUES ($1, 'IN_PROGRESS', 0) RETURNING id";
    private static final String SQL_SELECT_PROGRESS = "SELECT progress FROM tasks WHERE id = $1";
    private static final String SQL_UPDATE_PROGRESS = "UPDATE tasks SET status = $1, progress = $2 WHERE id = $3";

    private final Pool pool;

    /**
     * Создаёт репозиторий поверх пула соединений.
     *
     * @param pool пул соединений с PostgreSQL
     */
    public TaskRepository(Pool pool) {
        this.pool = pool;
    }

    /**
     * Создаёт новую задачу в статусе {@code IN_PROGRESS} с нулевым прогрессом.
     *
     * @param userId идентификатор пользователя-владельца
     * @return идентификатор созданной задачи
     */
    public Future<Integer> createTask(Integer userId) {
        return pool.preparedQuery(SQL_INSERT_TASK)
                .execute(Tuple.of(userId))
                .map(rowSet -> rowSet.iterator().next().getInteger("id"));
    }

    /**
     * Возвращает текущий прогресс задачи.
     *
     * @param taskId идентификатор задачи
     * @return значение прогресса либо {@code null}, если задачи с таким идентификатором нет
     */
    public Future<Integer> findProgress(Integer taskId) {
        return pool.preparedQuery(SQL_SELECT_PROGRESS)
                .execute(Tuple.of(taskId))
                .map(rowSet -> {
                    RowIterator<Row> iterator = rowSet.iterator();
                    return iterator.hasNext() ? iterator.next().getInteger("progress") : null;
                });
    }

    /**
     * Обновляет статус и прогресс задачи.
     *
     * @param taskId   идентификатор задачи
     * @param status   новый статус
     * @param progress новое значение прогресса
     * @return результат выполнения без полезной нагрузки
     */
    public Future<Void> updateProgress(Integer taskId, TaskStatus status, int progress) {
        return pool.preparedQuery(SQL_UPDATE_PROGRESS)
                .execute(Tuple.of(status.name(), progress, taskId))
                .mapEmpty();
    }
}
