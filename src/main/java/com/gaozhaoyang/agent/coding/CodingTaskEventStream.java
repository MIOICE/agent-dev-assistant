package com.gaozhaoyang.agent.coding;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * 向浏览器推送代码任务的最新完整快照。断线重连不需要补发所有增量事件，
 * 因为订阅建立后会先读取仓库中的最新状态。
 */
@Component
public class CodingTaskEventStream {

    static final long EMITTER_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long RECONNECT_TIME_MS = 1_500L;

    private final Map<String, CopyOnWriteArrayList<Client>> clients =
            new ConcurrentHashMap<>();
    private final LongFunction<SseEmitter> emitterFactory;

    public CodingTaskEventStream() {
        this(SseEmitter::new);
    }

    CodingTaskEventStream(LongFunction<SseEmitter> emitterFactory) {
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter subscribe(String taskId, Supplier<CodingTask> latestSnapshot) {
        SseEmitter emitter = emitterFactory.apply(EMITTER_TIMEOUT_MS);
        Client client = new Client(taskId, emitter);
        clients.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>())
                .add(client);
        emitter.onCompletion(() -> remove(client));
        emitter.onTimeout(() -> remove(client));
        emitter.onError(ignored -> remove(client));

        try {
            client.send(latestSnapshot.get());
        } catch (RuntimeException exception) {
            remove(client);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(CodingTask task) {
        List<Client> subscribers = clients.get(task.taskId());
        if (subscribers == null) {
            return;
        }
        for (Client client : subscribers) {
            try {
                client.send(task);
            } catch (RuntimeException exception) {
                remove(client);
                client.emitter().completeWithError(exception);
            }
        }
    }

    int subscriberCount(String taskId) {
        List<Client> subscribers = clients.get(taskId);
        return subscribers == null ? 0 : subscribers.size();
    }

    private void remove(Client client) {
        CopyOnWriteArrayList<Client> subscribers = clients.get(client.taskId());
        if (subscribers == null) {
            return;
        }
        subscribers.remove(client);
        if (subscribers.isEmpty()) {
            clients.remove(client.taskId(), subscribers);
        }
    }

    private static final class Client {
        private final String taskId;
        private final SseEmitter emitter;
        private Instant lastUpdatedAt;

        private Client(String taskId, SseEmitter emitter) {
            this.taskId = taskId;
            this.emitter = emitter;
        }

        private synchronized void send(CodingTask task) {
            if (lastUpdatedAt != null && task.updatedAt().isBefore(lastUpdatedAt)) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId(task))
                        .name("coding-task")
                        .reconnectTime(RECONNECT_TIME_MS)
                        .data(task));
                lastUpdatedAt = task.updatedAt();
            } catch (IOException | IllegalStateException exception) {
                throw new CodingTaskException("推送代码任务状态失败", exception);
            }
        }

        private String eventId(CodingTask task) {
            return task.updatedAt().toEpochMilli()
                    + "-" + task.events().size()
                    + "-" + task.agentLoop().consumedSteps();
        }

        private String taskId() {
            return taskId;
        }

        private SseEmitter emitter() {
            return emitter;
        }
    }
}
