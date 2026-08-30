package com.example.tasks.model;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskProgressTest {

    @Test
    void ofMarksInProgressBelowCompleteThreshold() {
        TaskProgress progress = TaskProgress.of(7, 3, 80);

        assertEquals(7, progress.taskId());
        assertEquals(3, progress.userId());
        assertEquals(80, progress.progress());
        assertEquals(TaskStatus.IN_PROGRESS, progress.status());
    }

    @Test
    void ofMarksCompletedAtCompleteThreshold() {
        TaskProgress progress = TaskProgress.of(1, 2, TaskProgress.COMPLETE_PROGRESS);

        assertEquals(TaskStatus.COMPLETED, progress.status());
        assertEquals(100, progress.progress());
    }

    @Test
    void roundTripsThroughJson() {
        TaskProgress original = TaskProgress.of(11, 22, 40);

        TaskProgress restored = TaskProgress.fromJson(original.toJson());

        assertEquals(original, restored);
        assertEquals(
                new JsonObject()
                        .put("taskId", 11)
                        .put("userId", 22)
                        .put("progress", 40)
                        .put("status", "IN_PROGRESS"),
                original.toJson());
    }
}
