package com.example.tasks.model;

/**
 * Статус фоновой задачи.
 *
 * <p>Имена констант совпадают со значениями, которые хранятся
 * в колонке {@code status} таблицы {@code tasks}.
 */
public enum TaskStatus {

    /** Задача в обработке. */
    IN_PROGRESS,

    /** Задача завершена. */
    COMPLETED
}
