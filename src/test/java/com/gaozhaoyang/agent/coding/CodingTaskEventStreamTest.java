package com.gaozhaoyang.agent.coding;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingTaskEventStreamTest {

    @Test
    void shouldSendLatestSnapshotAndIgnoreOlderConcurrentSnapshot() {
        RecordingEmitterFactory factory = new RecordingEmitterFactory();
        CodingTaskEventStream stream = new CodingTaskEventStream(factory::create);
        Instant now = Instant.now();
        CodingTask initial = task("task-1", now, 1);

        stream.subscribe("task-1", () -> initial);

        assertThat(factory.emitter.sendCount).isEqualTo(1);
        assertThat(stream.subscriberCount("task-1")).isEqualTo(1);

        stream.publish(task("task-1", now.plusSeconds(1), 2));
        stream.publish(task("task-1", now.minusSeconds(1), 0));

        assertThat(factory.emitter.sendCount).isEqualTo(2);
    }

    @Test
    void shouldRemoveBrokenSubscriberWithoutAffectingTaskExecution() {
        RecordingEmitterFactory factory = new RecordingEmitterFactory();
        CodingTaskEventStream stream = new CodingTaskEventStream(factory::create);
        Instant now = Instant.now();
        stream.subscribe("task-1", () -> task("task-1", now, 1));
        factory.emitter.fail = true;

        stream.publish(task("task-1", now.plusSeconds(1), 2));

        assertThat(stream.subscriberCount("task-1")).isZero();
    }

    private CodingTask task(String taskId, Instant updatedAt, int eventCount) {
        CodingTask task = mock(CodingTask.class);
        when(task.taskId()).thenReturn(taskId);
        when(task.updatedAt()).thenReturn(updatedAt);
        when(task.events()).thenReturn(java.util.Collections.nCopies(
                eventCount, CodingTaskEvent.of("TEST", "test")));
        AgentLoopState loop = AgentLoopState.initial(8);
        when(task.agentLoop()).thenReturn(loop);
        return task;
    }

    private static final class RecordingEmitterFactory {
        private RecordingEmitter emitter;

        private SseEmitter create(long timeout) {
            emitter = new RecordingEmitter(timeout);
            return emitter;
        }
    }

    private static final class RecordingEmitter extends SseEmitter {
        private int sendCount;
        private boolean fail;

        private RecordingEmitter(long timeout) {
            super(timeout);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            if (fail) {
                throw new IOException("client disconnected");
            }
            sendCount++;
        }
    }
}
