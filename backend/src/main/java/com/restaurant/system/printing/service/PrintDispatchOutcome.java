package com.restaurant.system.printing.service;

/** Terminal outcome for one durable order-dispatch outbox event. */
public enum PrintDispatchOutcome {
    DISPATCHED,
    MOCK_RENDERED,
    SKIPPED,
    POLICY_BLOCKED,
    CAPABILITY_UNAVAILABLE,
    FAILED
}
