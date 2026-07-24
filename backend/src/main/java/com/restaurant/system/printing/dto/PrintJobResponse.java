package com.restaurant.system.printing.dto;

import com.restaurant.system.printing.entity.PrintJob;
import java.time.LocalDateTime;

public class PrintJobResponse {

    public Long id;
    public Long organization_id;
    public Long store_id;
    public Long order_id;
    public Long order_update_batch_id;
    public Long printer_id;
    public String printer_name;
    public String printer_endpoint;
    public String module_code;
    public String receipt_type;
    public String status;
    public String execution_mode;
    public String rendered_text_snapshot;
    public String escpos_payload_base64;
    public String error_message;
    public String error_code;
    public String operator_message;
    public Integer retry_count;
    public Integer max_retry_count;
    public Long requested_by_user_id;
    public Long claimed_by_device_id;
    public LocalDateTime claimed_at;
    public LocalDateTime claim_expires_at;
    public Long printed_by_device_id;
    public String client_attempt_token;
    public LocalDateTime created_at;
    public LocalDateTime updated_at;
    public LocalDateTime printed_at;
    public LocalDateTime failed_at;
    public LocalDateTime last_attempt_at;
    public LocalDateTime attention_acknowledged_at;
    public Long attention_acknowledged_by;
    public String attention_acknowledgement_note;
    public String attention_acknowledged_status;
    public Integer attention_acknowledged_retry_count;
    public String attention_acknowledged_error_code;
    public boolean attention_acknowledged;

    public static PrintJobResponse from(PrintJob job, String printerName, String printerEndpoint) {
        PrintJobResponse response = new PrintJobResponse();
        response.id = job.id;
        response.organization_id = job.organization_id;
        response.store_id = job.store_id;
        response.order_id = job.order_id;
        response.order_update_batch_id = job.order_update_batch_id;
        response.printer_id = job.printer_id;
        response.printer_name = printerName;
        response.printer_endpoint = printerEndpoint;
        response.module_code = job.module_code;
        response.receipt_type = job.receipt_type;
        response.status = job.status;
        response.execution_mode = job.executionMode;
        response.rendered_text_snapshot = job.rendered_text_snapshot;
        response.escpos_payload_base64 = job.escposPayloadBase64;
        response.error_message = job.error_message;
        response.error_code = job.error_code;
        response.operator_message = operatorMessage(job);
        response.retry_count = job.retry_count;
        response.max_retry_count = job.max_retry_count;
        response.requested_by_user_id = job.requested_by_user_id;
        response.claimed_by_device_id = job.claimedByDeviceId;
        response.claimed_at = job.claimedAt;
        response.claim_expires_at = job.claimExpiresAt;
        response.printed_by_device_id = job.printedByDeviceId;
        response.client_attempt_token = job.clientAttemptToken;
        response.created_at = job.created_at;
        response.updated_at = job.updated_at;
        response.printed_at = job.printed_at;
        response.failed_at = job.failed_at;
        response.last_attempt_at = job.last_attempt_at;
        response.attention_acknowledged_at = job.attentionAcknowledgedAt;
        response.attention_acknowledged_by = job.attentionAcknowledgedBy;
        response.attention_acknowledgement_note = job.attentionAcknowledgementNote;
        response.attention_acknowledged_status = job.attentionAcknowledgedStatus;
        response.attention_acknowledged_retry_count = job.attentionAcknowledgedRetryCount;
        response.attention_acknowledged_error_code = job.attentionAcknowledgedErrorCode;
        response.attention_acknowledged = isAcknowledgedForCurrentState(job);
        return response;
    }

    private static boolean isAcknowledgedForCurrentState(PrintJob job) {
        return job.attentionAcknowledgedAt != null
            && equalsNormalized(job.attentionAcknowledgedStatus, job.status)
            && java.util.Objects.equals(job.attentionAcknowledgedRetryCount, job.retry_count)
            && equalsNormalized(job.attentionAcknowledgedErrorCode, job.error_code);
    }

    private static boolean equalsNormalized(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String operatorMessage(PrintJob job) {
        String status = normalize(job.status);
        String code = normalize(job.error_code);
        String module = moduleLabel(job);

        if ("PRINTED".equals(status)) {
            return null;
        }
        if ("PENDING".equals(status) && "PAD_DIRECT".equals(normalize(job.executionMode))) {
            return module + " is waiting for the Pad printer to pick it up.";
        }
        if (!"FAILED".equals(status) && !"CANCELLED".equals(status)) {
            return null;
        }

        return switch (code) {
            case "CLOUD_PRIVATE_PRINTER_BLOCKED" ->
                "Cloud server cannot directly connect to this LAN printer. Use PAD_DIRECT, MOCK, DISABLED, or a local print bridge.";
            case "PRINTING_DISABLED" ->
                "Automatic printing is disabled for this store. Orders are saved, but no physical ticket was printed.";
            case "ASSIGNMENT_MISSING" ->
                "No printer is assigned for " + module + ". Configure the printer assignment, then reprint.";
            case "ASSIGNMENT_DISABLED" ->
                module + " printing is disabled in printer assignments. Enable the assignment, then reprint.";
            case "PRINTER_MISSING" ->
                "The assigned printer for " + module + " no longer exists. Choose another printer assignment, then reprint.";
            case "PRINTER_DISABLED" ->
                "The assigned printer for " + module + " is disabled. Use an active printer assignment, then reprint.";
            case "RENDER_FAILED", "RENDERER_MISSING", "RENDER_DATA_MISSING", "RENDERED_CONTENT_BLANK" ->
                module + " could not be generated. Check the order content and contact support if it repeats.";
            case "CONNECTION_FAILED", "DISPATCH_ERROR", "TEST_PRINT_FAILED" ->
                module + " failed to print. Check printer power, IP/network, and reprint immediately.";
            case "REPRINT_FAILED", "ORDER_REPRINT_FAILED" ->
                "Reprint failed. Check printer status and try the reprint again.";
            case "PAD_DIRECT_FAILED" ->
                "Pad Direct reported this ticket failed. Check the Pad printer app and reprint.";
            case "PAD_DIRECT_RELEASED" ->
                "Pad Direct released this ticket before printing. It can be claimed again or reprinted.";
            default ->
                "FAILED".equals(status)
                    ? module + " failed to print. Reprint immediately after fixing the printer."
                    : module + " was not printed. Review the printer mode or assignment before service.";
        };
    }

    private static String moduleLabel(PrintJob job) {
        String receipt = normalize(job.receipt_type);
        String module = normalize(job.module_code);
        if (receipt.contains("GRAB_UPDATE")) {
            return "Kitchen update ticket";
        }
        if (receipt.contains("FRONTDESK_RECEIPT_UPDATE")) {
            return "Frontdesk update receipt";
        }
        if ("GRAB".equals(module)) {
            return "Kitchen ticket";
        }
        if ("FRONTDESK_RECEIPT".equals(module)) {
            return "Frontdesk receipt";
        }
        if (module.isBlank()) {
            return "Print job";
        }
        return module.replace('_', ' ');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
