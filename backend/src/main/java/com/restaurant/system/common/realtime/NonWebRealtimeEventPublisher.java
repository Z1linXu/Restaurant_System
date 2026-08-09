package com.restaurant.system.common.realtime;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("staging-synthetic-bootstrap")
public class NonWebRealtimeEventPublisher implements RealtimeEventPublisher {

    @Override
    public void publish(RealtimeUpdateMessage message, List<String> topicSuffixes) {
        // The guarded non-web one-shot has no connected realtime clients.
    }
}
