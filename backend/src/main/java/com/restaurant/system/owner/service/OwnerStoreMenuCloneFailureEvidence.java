package com.restaurant.system.owner.service;

public record OwnerStoreMenuCloneFailureEvidence(
    Long requestId,
    Long sourceMenuRevision,
    Long targetRevisionBefore,
    String errorCode
) {

    @Override
    public String toString() {
        return "OwnerStoreMenuCloneFailureEvidence[requestId=" + requestId + ", errorCode=redacted]";
    }
}
