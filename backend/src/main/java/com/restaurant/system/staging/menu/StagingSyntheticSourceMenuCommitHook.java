package com.restaurant.system.staging.menu;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("staging-synthetic-bootstrap")
public class StagingSyntheticSourceMenuCommitHook {

    public void beforeRevisionIncrement() {
        // Test seam for proving that a late failure rolls back the complete manifest.
    }
}
