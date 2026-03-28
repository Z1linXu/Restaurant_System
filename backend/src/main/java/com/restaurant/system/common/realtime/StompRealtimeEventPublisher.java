package com.restaurant.system.common.realtime;

import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class StompRealtimeEventPublisher implements RealtimeEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public StompRealtimeEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(RealtimeUpdateMessage message, List<String> topicSuffixes) {
        message.suggested_topics = topicSuffixes;
        Runnable sendAction = () -> {
            for (String topicSuffix : topicSuffixes) {
                messagingTemplate.convertAndSend("/topic/stores/" + message.store_id + "/" + topicSuffix, message);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAction.run();
                }
            });
            return;
        }
        sendAction.run();
    }
}
