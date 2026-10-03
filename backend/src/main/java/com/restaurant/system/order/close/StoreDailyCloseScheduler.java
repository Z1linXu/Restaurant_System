package com.restaurant.system.order.close;

import com.restaurant.system.user.repository.StoreRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!staging-synthetic-bootstrap")
@ConditionalOnProperty(name = "app.daily-close.enabled", havingValue = "true", matchIfMissing = true)
public class StoreDailyCloseScheduler {
    private static final Logger log = LoggerFactory.getLogger(StoreDailyCloseScheduler.class);
    private final StoreRepository stores;
    private final StoreDailyCloseService dailyClose;

    public StoreDailyCloseScheduler(StoreRepository stores, StoreDailyCloseService dailyClose) {
        this.stores = stores;
        this.dailyClose = dailyClose;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void closeDueStores() {
        closeDueStores(Instant.now());
    }

    void closeDueStores(Instant now) {
        for (var store : stores.findAllByStatusIgnoreCase("active")) {
            try {
                dailyClose.closeStore(store.id, now);
            } catch (RuntimeException exception) {
                // One Store failure must not suppress another Store's closing batch.
                log.warn("Daily auto Finish failed for Store {}; will retry", store.id, exception);
            }
        }
    }
}
