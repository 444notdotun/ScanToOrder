package com.scantoorder.scantoorder.controller;

import com.scantoorder.scantoorder.config.CurrentTenant;
import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.service.LiveUpdates;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Server-sent events: small notices that something changed, so a screen fetches it again.
 * A staff stream carries only its own business; a guest stream only its own session.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class StreamController {

    // The screen reconnects when this runs out, and fetches everything it may have missed.
    static final long TIMEOUT_MS = 30 * 60 * 1000L;

    @Autowired
    private LiveUpdates liveUpdates;

    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'CHEF')")
    @GetMapping(value = "/staff", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter staff() {
        String tenantId = CurrentTenant.id();
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        return attach(emitter, sink -> liveUpdates.subscribeStaff(tenantId, sink));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(value = "/guest", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter guest(@AuthenticationPrincipal CustomerPrincipal caller) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        return attach(emitter, sink -> liveUpdates.subscribeGuest(caller.getTenantId(), caller.getSessionId(), sink));
    }

    private SseEmitter attach(SseEmitter emitter, java.util.function.Function<java.util.function.Consumer<LiveUpdates.Notice>, LiveUpdates.Subscription> subscribe) {
        LiveUpdates.Subscription subscription = subscribe.apply(notice -> {
            try {
                emitter.send(SseEmitter.event().name(notice.type()).data(notice.json()));
            } catch (IOException | IllegalStateException e) {
                // The connection is gone. Throwing makes LiveUpdates forget it.
                throw new IllegalStateException(e);
            }
        });
        Runnable forget = () -> liveUpdates.unsubscribe(subscription);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"type\":\"connected\"}"));
        } catch (IOException e) {
            forget.run();
        }
        return emitter;
    }
}
