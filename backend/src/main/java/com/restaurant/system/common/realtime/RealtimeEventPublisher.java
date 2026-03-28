package com.restaurant.system.common.realtime;

import java.util.List;

public interface RealtimeEventPublisher {

    void publish(RealtimeUpdateMessage message, List<String> topicSuffixes);
}
