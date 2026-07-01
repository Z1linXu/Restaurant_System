package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.PrintJob;
import java.time.LocalDateTime;

public class PadPrintJobPayloadResponse {
    public Long job_id;
    public Long store_id;
    public Long order_id;
    public Long order_update_batch_id;
    public Long printer_id;
    public String module_code;
    public String receipt_type;
    public String rendered_text_snapshot;
    public String escpos_payload_base64;
    public Long claimed_by_device_id;
    public LocalDateTime claim_expires_at;
    public String client_attempt_token;

    public static PadPrintJobPayloadResponse from(PrintJob job) {
        PadPrintJobPayloadResponse response = new PadPrintJobPayloadResponse();
        response.job_id = job.id;
        response.store_id = job.store_id;
        response.order_id = job.order_id;
        response.order_update_batch_id = job.order_update_batch_id;
        response.printer_id = job.printer_id;
        response.module_code = job.module_code;
        response.receipt_type = job.receipt_type;
        response.rendered_text_snapshot = job.rendered_text_snapshot;
        response.escpos_payload_base64 = job.escposPayloadBase64;
        response.claimed_by_device_id = job.claimedByDeviceId;
        response.claim_expires_at = job.claimExpiresAt;
        response.client_attempt_token = job.clientAttemptToken;
        return response;
    }
}
