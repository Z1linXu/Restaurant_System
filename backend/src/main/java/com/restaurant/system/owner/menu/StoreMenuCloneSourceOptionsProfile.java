package com.restaurant.system.owner.menu;

import java.util.List;

/**
 * Optional profile capability that classifies reviewed source options for target items.
 */
public interface StoreMenuCloneSourceOptionsProfile extends StoreMenuCloneBaseGraphProfile {

    List<SourceOptionApplication> sourceOptionApplications();

    record SourceOptionApplication(
        String sourceItemSku,
        String targetItemSku,
        List<SourceOptionRule> rules
    ) {

        public SourceOptionApplication {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    record SourceOptionRule(
        String optionType,
        String optionGroup,
        SourceOptionDisposition disposition
    ) {
    }

    enum SourceOptionDisposition {
        COPY,
        PROFILE_OVERRIDE
    }
}
