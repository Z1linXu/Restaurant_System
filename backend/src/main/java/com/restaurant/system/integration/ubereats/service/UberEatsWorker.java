package com.restaurant.system.integration.ubereats.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "${UBER_EATS_WORKER_ENABLED:true}")
@Component
@Profile("!staging-synthetic-bootstrap")
@ConditionalOnProperty(name = "UBER_EATS_ENABLED", havingValue = "true")
public class UberEatsWorker {
    private final UberEatsOrderImportService service;

    public UberEatsWorker(UberEatsOrderImportService service) {
        this.service = service;
    }

    @Scheduled(scheduler = "uberEatsScheduler", fixedDelayString = "${UBER_EATS_POLL_MS:2000}")
    public void poll() {
        service.processEvents();
        service.recover();
    }
}
