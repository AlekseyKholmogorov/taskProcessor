package com.example.tasks.repository;

import com.example.tasks.model.TaskProgress;
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
    private static final String SQL_INCREMENT_PROGRESS = """
            UPDATE tasks
            SET progress = LEAST(progress + $1, $2), status = CASE WHEN progress + $1 >= $2 THEN $3 ELSE $4 END
            WHERE id = $5
            RETURNING user_id, progress, status
            """;

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
     * Атомарно увеличивает прогресс задачи на заданный шаг.
     *
     * <p>Весь переход «текущее значение плюс шаг» выполняется одним запросом:
     * чтение и запись не могут разъехаться между двумя конкурентными обновлениями.
     * При достижении порога завершения прогресс клампится на максимум,
     * а статус становится {@code COMPLETED}.
     *
     * @param taskId идентификатор задачи
     * @param step   величина приращения в процентах
     * @return новое состояние задачи либо {@code null}, если задачи не существует
     */
    public Future<TaskProgress> incrementProgress(Integer taskId, int step) {
        Tuple params = Tuple.of(
                step,
                TaskProgress.COMPLETE_PROGRESS,
                TaskStatus.COMPLETED.name(),
                TaskStatus.IN_PROGRESS.name(),
                taskId
        );

        return pool.preparedQuery(SQL_INCREMENT_PROGRESS)
                .execute(params)
                .map(rowSet -> {
                    RowIterator<Row> iterator = rowSet.iterator();
                    if (!iterator.hasNext()) {
                        return null;
                    }
                    Row row = iterator.next();
                    return new TaskProgress(
                            taskId,
                            row.getInteger("user_id"),
                            row.getInteger("progress"),
                            TaskStatus.valueOf(row.getString("status"))
                    );
                });
    }
}
