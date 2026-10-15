package com.restaurant.pad;

import java.net.HttpURLConnection;

/** Immutable origin/credential/lifecycle snapshot for the manual pending-list read. */
final class ManualPendingJobsRequest {
    private final String origin;
    private final String credentialOrigin;
    private final String deviceId;
    private final String token;
    private final String storeId;
    private final long generation;

    ManualPendingJobsRequest(String origin, String credentialOrigin, String deviceId, String token, String storeId, long generation) {
        this.origin = origin; this.credentialOrigin = credentialOrigin;
        this.deviceId = deviceId; this.token = token; this.storeId = storeId; this.generation = generation;
    }
    static boolean canStart(boolean workerBusy, boolean jobBusy, boolean nativeBusy, boolean manualReadBusy) {
        return !workerBusy && !jobBusy && !nativeBusy && !manualReadBusy;
    }
    HttpURLConnection open() throws Exception {
        return DeviceApiConnection.open(origin, credentialOrigin, deviceId, token,
            "/api/v1/stores/" + storeId + "/printing/jobs/pending?limit=25", "GET");
    }
    boolean isCurrent(long currentGeneration, String currentOrigin, String currentDeviceId) {
        return generation == currentGeneration && origin.equals(currentOrigin) && deviceId.equals(currentDeviceId);
    }
}
