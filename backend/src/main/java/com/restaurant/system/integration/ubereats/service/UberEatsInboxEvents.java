package com.restaurant.system.integration.ubereats.service;

import com.restaurant.system.common.realtime.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

import java.time.LocalDateTime;

@Component
public class UberEatsInboxEvents {
    public record Changed(Long storeId) {}

    private final ApplicationEventPublisher application;
    private final org.springframework.beans.factory.ObjectProvider<
                    org.springframework.messaging.simp.SimpMessagingTemplate>
            realtime;

    public UberEatsInboxEvents(
            ApplicationEventPublisher application,
            org.springframework.beans.factory.ObjectProvider<
                            org.springframework.messaging.simp.SimpMessagingTemplate>
                    realtime) {
        this.application = application;
        this.realtime = realtime;
    }

    public void changed(Long storeId) {
        application.publishEvent(new Changed(storeId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(Changed event) {
        var message = new RealtimeUpdateMessage();
        message.store_id = event.storeId();
        message.event_type = "uber.inbox.changed";
        message.happened_at = LocalDateTime.now();
        try {
            var template = realtime.getIfAvailable();
            if (template == null) return;
            template.convertAndSend(
                    "/topic/stores/" + event.storeId() + "/frontdesk/orders", message);
        } catch (RuntimeException ignored) {
            /* Inbox polling remains authoritative. */
        }
    }
}
