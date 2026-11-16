package com.scantoorder.scantoorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Small notices for the screens that are open right now: something changed, fetch it again.
 * A staff stream only ever carries its own business; a guest stream only its own session
 * (plus "menu", which carries nothing but the fact that the menu changed).
 * The open streams live in memory, so this works for one server instance. More instances would need
 * Redis pub/sub in between.
 */
@Slf4j
@Component
public class LiveUpdates {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // type is order, call, seat, table, menu, refund (or heartbeat). id and step are optional.
    public record Notice(String type, String id, String step) {
        public String json() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("type", type);
            if (id != null) node.put("id", id);
            if (step != null) node.put("step", step);
            return node.toString();
        }
    }

    public static final class Subscription {
        private final boolean staff;
        private final String tenantId;
        private final String sessionId;
        private final Consumer<Notice> sink;

        private Subscription(boolean staff, String tenantId, String sessionId, Consumer<Notice> sink) {
            this.staff = staff;
            this.tenantId = tenantId;
            this.sessionId = sessionId;
            this.sink = sink;
        }
    }

    private final Map<String, Set<Subscription>> byTenant = new ConcurrentHashMap<>();

    public Subscription subscribeStaff(String tenantId, Consumer<Notice> sink) {
        return add(new Subscription(true, tenantId, null, sink));
    }

    public Subscription subscribeGuest(String tenantId, String sessionId, Consumer<Notice> sink) {
        return add(new Subscription(false, tenantId, sessionId, sink));
    }

    public void unsubscribe(Subscription subscription) {
        if (subscription == null) return;
        Set<Subscription> set = byTenant.get(subscription.tenantId);
        if (set != null) {
            set.remove(subscription);
        }
    }

    public int openStreams() {
        return byTenant.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Tells every staff screen of the business, and the guests of one session when there is one.
     * It goes out once the change is saved, so a screen that refetches always sees it.
     */
    public void publish(String tenantId, String sessionId, String type, String id, String step) {
        if (tenantId == null) return;
        Notice notice = new Notice(type, id, step);
        Runnable send = () -> deliver(tenantId, sessionId, notice);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    public void publish(String tenantId, String type, String id, String step) {
        publish(tenantId, null, type, id, step);
    }

    // Keeps the connection open through proxies that close quiet ones (Render).
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        Notice beat = new Notice("heartbeat", null, null);
        for (Set<Subscription> set : byTenant.values()) {
            for (Subscription subscription : set) {
                push(subscription, beat);
            }
        }
    }

    private void deliver(String tenantId, String sessionId, Notice notice) {
        Set<Subscription> set = byTenant.get(tenantId);
        if (set == null) return;
        boolean menu = "menu".equals(notice.type());
        for (Subscription subscription : set) {
            boolean wanted = subscription.staff
                    || menu
                    || (sessionId != null && sessionId.equals(subscription.sessionId));
            if (wanted) {
                push(subscription, notice);
            }
        }
    }

    private void push(Subscription subscription, Notice notice) {
        try {
            subscription.sink.accept(notice);
        } catch (RuntimeException e) {
            // A closed or broken connection: drop it. The screen reconnects and refetches.
            unsubscribe(subscription);
        }
    }

    private Subscription add(Subscription subscription) {
        byTenant.computeIfAbsent(subscription.tenantId, key -> ConcurrentHashMap.newKeySet()).add(subscription);
        return subscription;
    }
}
